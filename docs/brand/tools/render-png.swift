// Renders a square brand SVG to a PNG for the app icons (macOS only).
//
// Usage (from the repository root):
//   swift docs/brand/tools/render-png.swift [--gray | --transparent] <input.svg> <output.png> <size>
//
// Modes:
//   (default)      opaque sRGB PNG without an alpha channel: iOS AppIcon.png, fastlane icon.png
//   --gray         opaque grayscale PNG (Gray Gamma 2.2) without an alpha channel: iOS AppIcon-Tinted.png
//   --transparent  sRGB PNG with alpha; the SVG's <rect> background elements are dropped first,
//                  only the mark stays: iOS AppIcon-Dark.png (iOS puts its own dark background behind it)
//
// Quick Look (`qlmanage -t`) rasterises the SVG with WebKit. It always returns a PNG with an
// alpha channel and puts transparent areas on white, so the default and --gray modes only suit
// the full-bleed icons (icon.svg, icon-tinted.svg). --transparent renders the mark once on black
// and once on white and recovers the alpha channel from the difference.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data("render-png: \(message)\n".utf8))
    exit(1)
}

enum Mode { case opaque, gray, transparent }

var args = Array(CommandLine.arguments.dropFirst())
var mode = Mode.opaque
if let flag = args.first, flag.hasPrefix("--") {
    switch flag {
    case "--gray": mode = .gray
    case "--transparent": mode = .transparent
    default: fail("unknown option \(flag)")
    }
    args.removeFirst()
}
guard args.count == 3, let size = Int(args[2]), size > 0 else {
    fail("usage: swift render-png.swift [--gray | --transparent] <input.svg> <output.png> <size>")
}
let input = URL(fileURLWithPath: args[0])
let output = URL(fileURLWithPath: args[1])
let workDir = FileManager.default.temporaryDirectory
    .appendingPathComponent("thrym-render-\(UUID().uuidString)")
try FileManager.default.createDirectory(at: workDir, withIntermediateDirectories: true)
defer { try? FileManager.default.removeItem(at: workDir) }

/// Rasterises an SVG with Quick Look at `size` x `size`.
func render(_ svg: URL) -> CGImage {
    let quickLook = Process()
    quickLook.executableURL = URL(fileURLWithPath: "/usr/bin/qlmanage")
    quickLook.arguments = ["-t", "-s", String(size), "-o", workDir.path, svg.path]
    quickLook.standardOutput = FileHandle.nullDevice
    quickLook.standardError = FileHandle.nullDevice
    do { try quickLook.run() } catch { fail("cannot run qlmanage: \(error)") }
    quickLook.waitUntilExit()
    let png = workDir.appendingPathComponent(svg.lastPathComponent + ".png")
    guard let source = CGImageSourceCreateWithURL(png as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        fail("Quick Look produced no image for \(svg.path)")
    }
    guard image.width == size, image.height == size else {
        fail("expected \(size)x\(size), Quick Look returned \(image.width)x\(image.height)")
    }
    return image
}

/// Premultiplied RGBA8 sRGB pixels of an image.
func pixels(_ image: CGImage) -> [UInt8] {
    var buffer = [UInt8](repeating: 0, count: size * size * 4)
    buffer.withUnsafeMutableBytes { bytes in
        let context = CGContext(data: bytes.baseAddress, width: size, height: size, bitsPerComponent: 8,
                                bytesPerRow: size * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    }
    return buffer
}

/// Draws an image onto black in a bitmap without alpha.
func flatten(_ image: CGImage, space: CFString, alphaInfo: CGImageAlphaInfo) -> CGImage {
    guard let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
                                  bytesPerRow: 0, space: CGColorSpace(name: space)!,
                                  bitmapInfo: alphaInfo.rawValue) else {
        fail("cannot create bitmap")
    }
    context.setFillColor(CGColor(gray: 0, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: size, height: size))
    context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
    return context.makeImage()!
}

/// Writes a copy of the SVG without its <rect> background elements, on a solid colour.
func markOn(_ color: String, name: String) -> URL {
    guard var svg = try? String(contentsOf: input, encoding: .utf8) else { fail("cannot read \(input.path)") }
    let rects = try! NSRegularExpression(pattern: "[ \\t]*<rect\\b[^>]*/>[ \\t]*\\n?")
    svg = rects.stringByReplacingMatches(in: svg, range: NSRange(svg.startIndex..., in: svg), withTemplate: "")
    guard let defsEnd = svg.range(of: "</defs>") else { fail("\(input.lastPathComponent) has no <defs>") }
    svg.insert(contentsOf: "\n  <rect width=\"100%\" height=\"100%\" fill=\"\(color)\"/>", at: defsEnd.upperBound)
    let url = workDir.appendingPathComponent(name)
    do { try svg.write(to: url, atomically: true, encoding: .utf8) } catch { fail("cannot write \(url.path)") }
    return url
}

let result: CGImage
switch mode {
case .opaque:
    result = flatten(render(input), space: CGColorSpace.sRGB, alphaInfo: .noneSkipLast)
case .gray:
    result = flatten(render(input), space: CGColorSpace.genericGrayGamma2_2, alphaInfo: .none)
case .transparent:
    // Over black a pixel is a*F, over white a*F + (1 - a)*255, so a = 1 - (white - black)/255.
    let onBlack = pixels(render(markOn("#000000", name: "on-black.svg")))
    let onWhite = pixels(render(markOn("#FFFFFF", name: "on-white.svg")))
    var premultiplied = [UInt8](repeating: 0, count: size * size * 4)
    for index in stride(from: 0, to: onBlack.count, by: 4) {
        var difference = 0
        for channel in 0..<3 { difference += Int(onWhite[index + channel]) - Int(onBlack[index + channel]) }
        let alpha = 255 - max(0, min(255, difference / 3))
        premultiplied[index + 3] = UInt8(alpha)
        for channel in 0..<3 { premultiplied[index + channel] = UInt8(min(Int(onBlack[index + channel]), alpha)) }
    }
    result = premultiplied.withUnsafeMutableBytes { bytes in
        CGContext(data: bytes.baseAddress, width: size, height: size, bitsPerComponent: 8,
                  bytesPerRow: size * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!.makeImage()!
    }
}

guard let destination = CGImageDestinationCreateWithURL(output as CFURL,
                                                        UTType.png.identifier as CFString, 1, nil) else {
    fail("cannot write \(output.path)")
}
CGImageDestinationAddImage(destination, result, nil)
guard CGImageDestinationFinalize(destination) else { fail("cannot write \(output.path)") }
let kind = mode == .transparent ? "sRGB with alpha" : (mode == .gray ? "gray, no alpha" : "sRGB, no alpha")
print("\(output.path): \(size)x\(size), \(kind)")
