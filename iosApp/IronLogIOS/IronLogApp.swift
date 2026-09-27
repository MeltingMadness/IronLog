import SwiftUI
import UIKit

@main
struct IronLogApp: App {
    init() {
        if let largeTitleFont = UIFont(name: "Geist-Bold", size: 34),
           let titleFont = UIFont(name: "Geist-SemiBold", size: 17) {
            UINavigationBar.appearance().largeTitleTextAttributes = [.font: largeTitleFont]
            UINavigationBar.appearance().titleTextAttributes = [.font: titleFont]
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
