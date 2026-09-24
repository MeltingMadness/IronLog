#!/usr/bin/env python3
"""Generate the native IronLog iOS Xcode project without third-party tools.

The repository keeps ``project.yml`` as the human-readable XcodeGen-shaped
description.  This script mirrors its current options in a deterministic
OpenStep project file so a clean checkout can create an Xcode project even
when Homebrew/XcodeGen is not installed.  Only the Swift source inventory and
the generated project metadata are written; no build or network action runs.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


PROJECT_NAME = "IronLogIOS"
APP_BUNDLE_IDENTIFIER = "com.ironlog.ios"
TEST_BUNDLE_IDENTIFIER = "com.ironlog.ios.tests"
DEPLOYMENT_TARGET = "17.0"
SWIFT_VERSION = "5.10"
PROJECT_OBJECT_VERSION = "56"
XCODE_UPGRADE_VERSION = "1600"


@dataclass(frozen=True)
class InventoryFile:
    target: str
    relative_path: str
    reference_id: str
    build_file_id: str | None
    file_type: str


@dataclass
class ProjectInventory:
    file_references: dict[str, InventoryFile] = field(default_factory=dict)
    build_files: dict[str, tuple[str, str]] = field(default_factory=dict)
    source_build_ids: dict[str, list[str]] = field(default_factory=lambda: {"app": [], "tests": []})
    resource_build_ids: dict[str, list[str]] = field(default_factory=lambda: {"app": [], "tests": []})
    groups: dict[str, dict[str, object]] = field(default_factory=dict)


def stable_id(kind: str, key: str) -> str:
    """Return an Xcode-style 24-character identifier stable across runs."""

    digest = hashlib.sha1(f"{kind}:{key}".encode("utf-8")).hexdigest().upper()
    return digest[:24]


def pbx_quote(value: str) -> str:
    """Quote an OpenStep value only when its characters require quoting."""

    # Xcode's OpenStep parser accepts simple identifiers and paths without
    # quotes. Variable references (`$(CONFIGURATION)`, `$(SRCROOT)`, ...)
    # contain punctuation that must remain inside a quoted value.
    if re.fullmatch(r"[A-Za-z0-9_./@+\-]+", value):
        return value
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
    return f'"{escaped}"'


def pbx_comment(value: str) -> str:
    return value.replace("/*", "").replace("*/", "")


def add_file(
    inventory: ProjectInventory,
    *,
    target: str,
    root: Path,
    path: Path,
    file_type: str,
    build_phase: str | None,
) -> InventoryFile:
    relative_path = path.relative_to(root).as_posix()
    key = f"{target}:{relative_path}"
    reference_id = stable_id("file", key)
    build_file_id = stable_id("build", f"{build_phase}:{key}") if build_phase else None
    inventory.file_references[reference_id] = InventoryFile(
        target=target,
        relative_path=relative_path,
        reference_id=reference_id,
        build_file_id=build_file_id,
        file_type=file_type,
    )
    if build_file_id:
        inventory.build_files[build_file_id] = (reference_id, path.name)
        if build_phase == "Sources":
            inventory.source_build_ids[target].append(build_file_id)
        elif build_phase == "Resources":
            inventory.resource_build_ids[target].append(build_file_id)
    return inventory.file_references[reference_id]


def resource_file_type(path: Path) -> str:
    """Return the Xcode file type for a regular resource file."""

    return {
        ".json": "text.json",
        ".strings": "text.plist.strings",
        ".stringsdict": "text.plist.stringsdict",
        ".storyboard": "file.storyboard",
        ".xib": "file.xib",
        ".metal": "sourcecode.metal",
        ".png": "image.png",
        ".jpg": "image.jpeg",
        ".jpeg": "image.jpeg",
        ".heic": "image.heic",
        ".gif": "image.gif",
        ".wav": "audio.wav",
        ".mp3": "audio.mp3",
        ".m4a": "audio.m4a",
        ".mov": "video.quicktime",
        ".mp4": "video.mp4",
    }.get(path.suffix.lower(), "file")


# Xcode treats these directory packages as one resource. Recursing into an
# asset catalog would incorrectly add its internal JSON and image files as
# separate resources.
PACKAGE_RESOURCE_TYPES = {
    ".appiconset": "folder.assetcatalog",
    ".colorset": "folder.assetcatalog",
    ".dataset": "folder.dataset",
    ".imageset": "folder.assetcatalog",
    ".launchimage": "folder.assetcatalog",
    ".xcassets": "folder.assetcatalog",
}


def collect_group(
    inventory: ProjectInventory,
    *,
    root: Path,
    directory: Path,
    target: str,
    group_key: str,
    group_name: str,
    include_info_plist: bool = False,
) -> str:
    """Collect Swift files and nested directory groups in sorted order."""

    group_id = stable_id("group", group_key)
    children: list[tuple[str, str]] = []
    entries = sorted(directory.iterdir(), key=lambda item: (not item.is_dir(), item.name.lower()))

    for entry in entries:
        if entry.name.startswith("."):
            continue
        if entry.is_dir():
            package_type = PACKAGE_RESOURCE_TYPES.get(entry.suffix.lower())
            if package_type:
                resource = add_file(
                    inventory,
                    target=target,
                    root=root,
                    path=entry,
                    file_type=package_type,
                    build_phase="Resources",
                )
                children.append((resource.reference_id, entry.name))
                continue
            child_id = collect_group(
                inventory,
                root=root,
                directory=entry,
                target=target,
                group_key=f"{group_key}/{entry.name}",
                group_name=entry.name,
                include_info_plist=False,
            )
            child = inventory.groups[child_id]
            if child["children"]:
                children.append((child_id, str(child["name"])))
            continue

        if entry.suffix == ".swift":
            file = add_file(
                inventory,
                target=target,
                root=root,
                path=entry,
                file_type="sourcecode.swift",
                build_phase="Sources",
            )
            children.append((file.reference_id, entry.name))
        elif include_info_plist and entry.name == "Info.plist":
            file = add_file(
                inventory,
                target=target,
                root=root,
                path=entry,
                file_type="text.plist.xml",
                build_phase=None,
            )
            children.append((file.reference_id, entry.name))
        elif entry.name != "Info.plist":
            resource = add_file(
                inventory,
                target=target,
                root=root,
                path=entry,
                file_type=resource_file_type(entry),
                build_phase="Resources",
            )
            children.append((resource.reference_id, entry.name))

    inventory.groups[group_id] = {
        "name": group_name,
        # File/group references use <group> sourceTree.  Each group's path is
        # therefore relative to its parent group; the root groups happen to
        # have the same name as their directory on disk.
        "path": directory.name,
        "children": children,
    }
    return group_id


def collect_inventory(project_root: Path) -> tuple[ProjectInventory, dict[str, str]]:
    inventory = ProjectInventory()
    app_root = project_root / "IronLogIOS"
    tests_root = project_root / "IronLogIOSTests"

    app_group_id = collect_group(
        inventory,
        root=project_root,
        directory=app_root,
        target="app",
        group_key="app",
        group_name="IronLogIOS",
        include_info_plist=True,
    )
    tests_group_id = collect_group(
        inventory,
        root=project_root,
        directory=tests_root,
        target="tests",
        group_key="tests",
        group_name="IronLogIOSTests",
    )

    products_group_id = stable_id("group", "products")
    frameworks_group_id = stable_id("group", "frameworks")
    inventory.groups[products_group_id] = {
        "name": "Products",
        "path": None,
        "children": [],
    }
    inventory.groups[frameworks_group_id] = {
        "name": "Frameworks",
        "path": None,
        "children": [],
    }

    shared_reference_id = stable_id("file", "framework:Shared.framework")
    inventory.file_references[shared_reference_id] = InventoryFile(
        target="framework",
        relative_path="../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/Shared.framework",
        reference_id=shared_reference_id,
        build_file_id=None,
        file_type="wrapper.framework",
    )
    inventory.groups[frameworks_group_id]["children"] = [(shared_reference_id, "Shared.framework")]

    app_product_reference_id = stable_id("file", "product:IronLogIOS.app")
    tests_product_reference_id = stable_id("file", "product:IronLogIOSTests.xctest")
    inventory.file_references[app_product_reference_id] = InventoryFile(
        target="product",
        relative_path="IronLogIOS.app",
        reference_id=app_product_reference_id,
        build_file_id=None,
        file_type="wrapper.application",
    )
    inventory.file_references[tests_product_reference_id] = InventoryFile(
        target="product",
        relative_path="IronLogIOSTests.xctest",
        reference_id=tests_product_reference_id,
        build_file_id=None,
        file_type="wrapper.cfbundle",
    )
    inventory.groups[products_group_id]["children"] = [
        (app_product_reference_id, "IronLogIOS.app"),
        (tests_product_reference_id, "IronLogIOSTests.xctest"),
    ]

    project_group_id = stable_id("group", "project")
    inventory.groups[project_group_id] = {
        "name": None,
        "path": None,
        "children": [
            (app_group_id, "IronLogIOS"),
            (tests_group_id, "IronLogIOSTests"),
            (products_group_id, "Products"),
            (frameworks_group_id, "Frameworks"),
        ],
    }

    return inventory, {
        "app_group": app_group_id,
        "tests_group": tests_group_id,
        "products_group": products_group_id,
        "frameworks_group": frameworks_group_id,
        "project_group": project_group_id,
        "shared_reference": shared_reference_id,
        "app_product": app_product_reference_id,
        "tests_product": tests_product_reference_id,
    }


def render_children(children: Iterable[tuple[str, str]], *, indent: str = "\t\t") -> list[str]:
    lines = [f"{indent}children = ("]
    for identifier, comment in children:
        lines.append(f"{indent}\t{identifier} /* {pbx_comment(comment)} */,")
    lines.append(f"{indent});")
    return lines


def render_pbxproj(project_root: Path) -> tuple[str, dict[str, str]]:
    inventory, groups = collect_inventory(project_root)
    project_id = stable_id("project", PROJECT_NAME)
    app_target_id = stable_id("target", "app")
    tests_target_id = stable_id("target", "tests")

    phase_ids = {
        "app_sources": stable_id("phase", "app:Sources"),
        "app_resources": stable_id("phase", "app:Resources"),
        "app_frameworks": stable_id("phase", "app:Frameworks"),
        "app_prebuild": stable_id("phase", "app:BuildSharedFramework"),
        "tests_sources": stable_id("phase", "tests:Sources"),
        "tests_resources": stable_id("phase", "tests:Resources"),
        "tests_frameworks": stable_id("phase", "tests:Frameworks"),
    }
    shared_build_file_id = stable_id("build", "app:Frameworks:framework:Shared.framework")
    inventory.build_files[shared_build_file_id] = (
        groups["shared_reference"],
        "Shared.framework",
    )

    config_ids = {
        "project": {name: stable_id("config", f"project:{name}") for name in ("Debug", "Release")},
        "app": {name: stable_id("config", f"app:{name}") for name in ("Debug", "Release")},
        "tests": {name: stable_id("config", f"tests:{name}") for name in ("Debug", "Release")},
    }
    config_lists = {
        "project": stable_id("config-list", "project"),
        "app": stable_id("config-list", "app"),
        "tests": stable_id("config-list", "tests"),
    }
    proxy_id = stable_id("proxy", "tests:app")
    dependency_id = stable_id("dependency", "tests:app")

    lines: list[str] = [
        "// !$*UTF8*$!",
        "{",
        "\tarchiveVersion = 1;",
        "\tclasses = {",
        "\t};",
        f"\tobjectVersion = {PROJECT_OBJECT_VERSION};",
        "\tobjects = {",
        "",
        "/* Begin PBXBuildFile section */",
    ]

    for build_id in sorted(inventory.build_files):
        reference_id, comment = inventory.build_files[build_id]
        if build_id == shared_build_file_id:
            suffix = " in Frameworks"
        elif build_id in inventory.resource_build_ids["app"] or build_id in inventory.resource_build_ids["tests"]:
            suffix = " in Resources"
        else:
            suffix = " in Sources"
        lines.append(
            f"\t\t{build_id} /* {pbx_comment(comment)}{suffix} */ = {{isa = PBXBuildFile; "
            f"fileRef = {reference_id} /* {pbx_comment(comment)} */; }};"
        )
    lines.extend(["/* End PBXBuildFile section */", "", "/* Begin PBXFileReference section */"])

    for reference_id in sorted(inventory.file_references):
        file = inventory.file_references[reference_id]
        if file.target == "product":
            source_tree = "BUILT_PRODUCTS_DIR"
            path = file.relative_path
            name = Path(path).name
        elif file.target == "framework":
            source_tree = "SOURCE_ROOT"
            path = file.relative_path
            name = "Shared.framework"
        else:
            source_tree = '"<group>"'
            # Nested PBXGroups already carry the directory path.  A file
            # reference below one of them must use only its local filename.
            path = Path(file.relative_path).name
            name = Path(path).name
        name_clause = f"name = {pbx_quote(name)}; " if file.target in {"product", "framework"} else ""
        lines.append(
            f"\t\t{reference_id} /* {pbx_comment(name)} */ = {{isa = PBXFileReference; "
            f"lastKnownFileType = {file.file_type}; {name_clause}path = {pbx_quote(path)}; "
            f"sourceTree = {source_tree}; }};"
        )
    lines.extend(["/* End PBXFileReference section */", "", "/* Begin PBXFrameworksBuildPhase section */"])

    lines.extend(
        [
            f"\t\t{phase_ids['app_frameworks']} /* Frameworks */ = {{",
            "\t\t\tisa = PBXFrameworksBuildPhase;",
            "\t\t\tbuildActionMask = 2147483647;",
            "\t\t\tfiles = (",
            f"\t\t\t\t{shared_build_file_id} /* Shared.framework in Frameworks */ ,",
            "\t\t\t);",
            "\t\t\trunOnlyForDeploymentPostprocessing = 0;",
            "\t\t};",
            f"\t\t{phase_ids['tests_frameworks']} /* Frameworks */ = {{",
            "\t\t\tisa = PBXFrameworksBuildPhase;",
            "\t\t\tbuildActionMask = 2147483647;",
            "\t\t\tfiles = (",
            "\t\t\t);",
            "\t\t\trunOnlyForDeploymentPostprocessing = 0;",
            "\t\t};",
            "/* End PBXFrameworksBuildPhase section */",
            "",
            "/* Begin PBXGroup section */",
        ]
    )

    for group_id in sorted(inventory.groups):
        group = inventory.groups[group_id]
        name = group["name"]
        path = group["path"]
        lines.append(f"\t\t{group_id} /* {pbx_comment(str(name or PROJECT_NAME))} */ = {{")
        lines.append("\t\t\tisa = PBXGroup;")
        lines.extend(render_children(group["children"]))
        if name and path is None:
            lines.append(f"\t\t\tname = {pbx_quote(str(name))};")
        if path:
            lines.append(f"\t\t\tpath = {pbx_quote(str(path))};")
        lines.append('\t\t\tsourceTree = "<group>";')
        lines.append("\t\t};")
    lines.extend(["/* End PBXGroup section */", "", "/* Begin PBXNativeTarget section */"])

    lines.extend(
        [
            f"\t\t{app_target_id} /* {PROJECT_NAME} */ = {{",
            "\t\t\tisa = PBXNativeTarget;",
            f"\t\t\tbuildConfigurationList = {config_lists['app']} /* Build configuration list for PBXNativeTarget \"{PROJECT_NAME}\" */;",
            "\t\t\tbuildPhases = (",
            f"\t\t\t\t{phase_ids['app_prebuild']} /* Build Shared Framework */,",
            f"\t\t\t\t{phase_ids['app_sources']} /* Sources */,",
            f"\t\t\t\t{phase_ids['app_frameworks']} /* Frameworks */,",
            f"\t\t\t\t{phase_ids['app_resources']} /* Resources */,",
            "\t\t\t);",
            "\t\t\tbuildRules = (",
            "\t\t\t);",
            "\t\t\tdependencies = (",
            "\t\t\t);",
            f"\t\t\tname = {PROJECT_NAME};",
            f"\t\t\tproductName = {PROJECT_NAME};",
            f"\t\t\tproductReference = {groups['app_product']} /* {PROJECT_NAME}.app */;",
            "\t\t\tproductType = \"com.apple.product-type.application\";",
            "\t\t};",
            f"\t\t{tests_target_id} /* IronLogIOSTests */ = {{",
            "\t\t\tisa = PBXNativeTarget;",
            f"\t\t\tbuildConfigurationList = {config_lists['tests']} /* Build configuration list for PBXNativeTarget \"IronLogIOSTests\" */;",
            "\t\t\tbuildPhases = (",
            f"\t\t\t\t{phase_ids['tests_sources']} /* Sources */,",
            f"\t\t\t\t{phase_ids['tests_frameworks']} /* Frameworks */,",
            f"\t\t\t\t{phase_ids['tests_resources']} /* Resources */,",
            "\t\t\t);",
            "\t\t\tbuildRules = (",
            "\t\t\t);",
            "\t\t\tdependencies = (",
            f"\t\t\t\t{dependency_id} /* PBXTargetDependency */,",
            "\t\t\t);",
            "\t\t\tname = IronLogIOSTests;",
            "\t\t\tproductName = IronLogIOSTests;",
            f"\t\t\tproductReference = {groups['tests_product']} /* IronLogIOSTests.xctest */;",
            "\t\t\tproductType = \"com.apple.product-type.bundle.unit-test\";",
            "\t\t};",
            "/* End PBXNativeTarget section */",
            "",
            "/* Begin PBXProject section */",
            f"\t\t{project_id} /* Project object */ = {{",
            "\t\t\tisa = PBXProject;",
            "\t\t\tattributes = {",
            f"\t\t\t\tLastUpgradeCheck = {XCODE_UPGRADE_VERSION};",
            "\t\t\t\tTargetAttributes = {",
            f"\t\t\t\t\t{app_target_id} = {{ CreatedOnToolsVersion = {XCODE_UPGRADE_VERSION}; }};",
            f"\t\t\t\t\t{tests_target_id} = {{ CreatedOnToolsVersion = {XCODE_UPGRADE_VERSION}; TestTargetID = {app_target_id}; }};",
            "\t\t\t\t};",
            "\t\t\t};",
            f"\t\t\tbuildConfigurationList = {config_lists['project']} /* Build configuration list for PBXProject \"{PROJECT_NAME}\" */;",
            '\t\t\tcompatibilityVersion = "Xcode 15.0";',
            "\t\t\tdevelopmentRegion = en;",
            "\t\t\thasScannedForEncodings = 0;",
            "\t\t\tknownRegions = (",
            "\t\t\t\ten,",
            "\t\t\t\tBase,",
            "\t\t\t);",
            f"\t\t\tmainGroup = {groups['project_group']} /* {PROJECT_NAME} */;",
            f"\t\t\tproductRefGroup = {groups['products_group']} /* Products */;",
            "\t\t\tprojectDirPath = \"\";",
            "\t\t\tprojectRoot = \"\";",
            "\t\t\ttargets = (",
            f"\t\t\t\t{app_target_id} /* {PROJECT_NAME} */,",
            f"\t\t\t\t{tests_target_id} /* IronLogIOSTests */,",
            "\t\t\t);",
            "\t\t};",
            "/* End PBXProject section */",
            "",
            "/* Begin PBXResourcesBuildPhase section */",
        ]
    )

    for phase_id, comment in (
        (phase_ids["app_resources"], "Resources"),
        (phase_ids["tests_resources"], "Resources"),
    ):
        target = "app" if phase_id == phase_ids["app_resources"] else "tests"
        lines.extend(
            [
                f"\t\t{phase_id} /* {comment} */ = {{",
                "\t\t\tisa = PBXResourcesBuildPhase;",
                "\t\t\tbuildActionMask = 2147483647;",
                "\t\t\tfiles = (",
            ]
        )
        for build_id in inventory.resource_build_ids[target]:
            reference_id, comment_name = inventory.build_files[build_id]
            del reference_id
            lines.append(f"\t\t\t\t{build_id} /* {pbx_comment(comment_name)} in Resources */, ")
        lines.extend(
            [
                "\t\t\t);",
                "\t\t\trunOnlyForDeploymentPostprocessing = 0;",
                "\t\t};",
            ]
        )
    lines.extend(["/* End PBXResourcesBuildPhase section */", "", "/* Begin PBXShellScriptBuildPhase section */"])
    script = "set -euo pipefail\n\"$SRCROOT/scripts/build-shared.sh\"\n"
    lines.extend(
        [
            f"\t\t{phase_ids['app_prebuild']} /* Build Shared Framework */ = {{",
            "\t\t\tisa = PBXShellScriptBuildPhase;",
            "\t\t\tbuildActionMask = 2147483647;",
            "\t\t\tfiles = (",
            "\t\t\t);",
            "\t\t\tinputFileListPaths = (",
            "\t\t\t);",
            "\t\t\tinputPaths = (",
            "\t\t\t);",
            '\t\t\tname = "Build Shared Framework";',
            "\t\t\toutputFileListPaths = (",
            "\t\t\t);",
            "\t\t\toutputPaths = (",
            "\t\t\t);",
            "\t\t\trunOnlyForDeploymentPostprocessing = 0;",
            "\t\t\tshellPath = /bin/bash;",
            f"\t\t\tshellScript = {pbx_quote(script)};",
            "\t\t\tshowEnvVarsInLog = 0;",
            "\t\t};",
            "/* End PBXShellScriptBuildPhase section */",
            "",
            "/* Begin PBXSourcesBuildPhase section */",
        ]
    )
    for phase_id, target, comment in (
        (phase_ids["app_sources"], "app", "Sources"),
        (phase_ids["tests_sources"], "tests", "Sources"),
    ):
        lines.extend(
            [
                f"\t\t{phase_id} /* {comment} */ = {{",
                "\t\t\tisa = PBXSourcesBuildPhase;",
                "\t\t\tbuildActionMask = 2147483647;",
                "\t\t\tfiles = (",
            ]
        )
        for build_id in inventory.source_build_ids[target]:
            reference_id, comment_name = inventory.build_files[build_id]
            del reference_id
            lines.append(f"\t\t\t\t{build_id} /* {pbx_comment(comment_name)} in Sources */, ")
        lines.extend(
            [
                "\t\t\t);",
                "\t\t\trunOnlyForDeploymentPostprocessing = 0;",
                "\t\t};",
            ]
        )
    lines.extend(["/* End PBXSourcesBuildPhase section */", "", "/* Begin PBXTargetDependency section */"])
    lines.extend(
        [
            f"\t\t{dependency_id} /* PBXTargetDependency */ = {{",
            "\t\t\tisa = PBXTargetDependency;",
            f"\t\t\ttarget = {app_target_id} /* {PROJECT_NAME} */;",
            f"\t\t\ttargetProxy = {proxy_id} /* PBXContainerItemProxy */;",
            "\t\t};",
            "/* End PBXTargetDependency section */",
            "",
            "/* Begin PBXContainerItemProxy section */",
            f"\t\t{proxy_id} /* PBXContainerItemProxy */ = {{",
            "\t\t\tisa = PBXContainerItemProxy;",
            f"\t\t\tcontainerPortal = {project_id} /* Project object */;",
            "\t\t\tproxyType = 1;",
            f"\t\t\tremoteGlobalIDString = {app_target_id};",
            f"\t\t\tremoteInfo = {PROJECT_NAME};",
            "\t\t};",
            "/* End PBXContainerItemProxy section */",
            "",
            "/* Begin XCBuildConfiguration section */",
        ]
    )

    project_common = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "SDKROOT": "iphoneos",
        "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator",
        "SWIFT_VERSION": SWIFT_VERSION,
    }
    app_common = {
        "CODE_SIGN_STYLE": "Automatic",
        "DEVELOPMENT_TEAM": "",
        "FRAMEWORK_SEARCH_PATHS": "$(inherited) $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)",
        "INFOPLIST_FILE": "IronLogIOS/Info.plist",
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        "LD_RUNPATH_SEARCH_PATHS": "$(inherited) @executable_path/Frameworks",
        "PRODUCT_BUNDLE_IDENTIFIER": APP_BUNDLE_IDENTIFIER,
        "PRODUCT_NAME": PROJECT_NAME,
        "TARGETED_DEVICE_FAMILY": "1",
    }
    tests_common = {
        "BUNDLE_LOADER": "$(TEST_HOST)",
        "CODE_SIGN_STYLE": "Automatic",
        "DEVELOPMENT_TEAM": "",
        "GENERATE_INFOPLIST_FILE": "YES",
        "LD_RUNPATH_SEARCH_PATHS": "$(inherited) @loader_path/Frameworks @executable_path/Frameworks",
        "PRODUCT_BUNDLE_IDENTIFIER": TEST_BUNDLE_IDENTIFIER,
        "PRODUCT_NAME": "IronLogIOSTests",
        "TARGETED_DEVICE_FAMILY": "1",
        "TEST_HOST": "$(BUILT_PRODUCTS_DIR)/IronLogIOS.app/IronLogIOS",
    }

    def render_config(config_id: str, name: str, settings: dict[str, str]) -> None:
        lines.append(f"\t\t{config_id} /* {name} */ = {{")
        lines.append("\t\t\tisa = XCBuildConfiguration;")
        lines.append("\t\t\tbuildSettings = {")
        for key in sorted(settings):
            lines.append(f"\t\t\t\t{key} = {pbx_quote(settings[key])};")
        lines.extend(["\t\t\t};", f"\t\t\tname = {name};", "\t\t};"])

    for name in ("Debug", "Release"):
        render_config(
            config_ids["project"][name],
            name,
            {
                **project_common,
                "PRODUCT_NAME": PROJECT_NAME,
            },
        )
    for name in ("Debug", "Release"):
        render_config(
            config_ids["app"][name],
            name,
            {
                **app_common,
                "ENABLE_TESTABILITY": "YES" if name == "Debug" else "NO",
                "GENERATE_INFOPLIST_FILE": "NO",
                "SWIFT_OPTIMIZATION_LEVEL": "-Onone" if name == "Debug" else "-O",
                **project_common,
            },
        )
    for name in ("Debug", "Release"):
        render_config(
            config_ids["tests"][name],
            name,
            {
                **tests_common,
                "SWIFT_OPTIMIZATION_LEVEL": "-Onone" if name == "Debug" else "-O",
                **project_common,
            },
        )
    lines.extend(["/* End XCBuildConfiguration section */", "", "/* Begin XCConfigurationList section */"])
    for owner in ("project", "app", "tests"):
        lines.extend(
            [
                f"\t\t{config_lists[owner]} /* Build configuration list for {('PBXProject' if owner == 'project' else 'PBXNativeTarget')} \"{PROJECT_NAME if owner != 'tests' else 'IronLogIOSTests'}\" */ = {{",
                "\t\t\tisa = XCConfigurationList;",
                "\t\t\tbuildConfigurations = (",
                f"\t\t\t\t{config_ids[owner]['Debug']} /* Debug */,",
                f"\t\t\t\t{config_ids[owner]['Release']} /* Release */,",
                "\t\t\t);",
                "\t\t\tdefaultConfigurationIsVisible = 0;",
                "\t\t\tdefaultConfigurationName = Release;",
                "\t\t};",
            ]
        )
    lines.extend(
        [
            "/* End XCConfigurationList section */",
            "\t};",
            "\trootObject = " + project_id + " /* Project object */;",
            "}",
            "",
        ]
    )
    return "\n".join(lines), {
        "project": project_id,
        "app_target": app_target_id,
        "tests_target": tests_target_id,
        "app_product_name": "IronLogIOS.app",
        "tests_product_name": "IronLogIOSTests.xctest",
    }


def render_scheme(ids: dict[str, str]) -> str:
    container = f"container:{PROJECT_NAME}.xcodeproj"
    app_ref = (
        f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ids["app_target"]}" '
        f'BuildableName="{ids["app_product_name"]}" BlueprintName="{PROJECT_NAME}" '
        f'ReferencedContainer="{container}" />'
    )
    tests_ref = (
        f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{ids["tests_target"]}" '
        f'BuildableName="{ids["tests_product_name"]}" BlueprintName="IronLogIOSTests" '
        f'ReferencedContainer="{container}" />'
    )
    return "\n".join(
        [
            '<?xml version="1.0" encoding="UTF-8"?>',
            '<Scheme LastUpgradeVersion="1600" version="1.7">',
            '   <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">',
            '      <BuildActionEntries>',
            '         <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">',
            f"            {app_ref}",
            '         </BuildActionEntry>',
            '         <BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO">',
            f"            {tests_ref}",
            '         </BuildActionEntry>',
            '      </BuildActionEntries>',
            '   </BuildAction>',
            '   <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.DebuggerFoundation.Launcher.LLDBLauncher" shouldUseLaunchSchemeArgsEnv="YES" codeCoverageEnabled="YES">',
            '      <Testables>',
            '         <TestableReference skipped="NO">',
            f"            {tests_ref}",
            '         </TestableReference>',
            '      </Testables>',
            '   </TestAction>',
            '   <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.DebuggerFoundation.Launcher.LLDBLauncher" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersion="0" debugServiceExtension="internal" allowLocationSimulation="YES">',
            '      <BuildableProductRunnable runnableDebuggingMode="0">',
            f"         {app_ref}",
            '      </BuildableProductRunnable>',
            '   </LaunchAction>',
            '   <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersion="0">',
            '      <BuildableProductRunnable runnableDebuggingMode="0">',
            f"         {app_ref}",
            '      </BuildableProductRunnable>',
            '   </ProfileAction>',
            '   <AnalyzeAction buildConfiguration="Debug" codeCoverageEnabled="YES" />',
            '   <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES" />',
            '</Scheme>',
            "",
        ]
    )


def main() -> None:
    script_path = Path(__file__).resolve()
    ios_root = script_path.parent
    app_root = ios_root / "IronLogIOS"
    tests_root = ios_root / "IronLogIOSTests"
    if not app_root.is_dir() or not tests_root.is_dir():
        raise SystemExit("IronLogIOS and IronLogIOSTests source directories are required")

    pbxproj, ids = render_pbxproj(ios_root)
    project_dir = ios_root / f"{PROJECT_NAME}.xcodeproj"
    scheme_dir = project_dir / "xcshareddata" / "xcschemes"
    project_dir.mkdir(parents=True, exist_ok=True)
    scheme_dir.mkdir(parents=True, exist_ok=True)
    (project_dir / "project.pbxproj").write_text(pbxproj, encoding="utf-8")
    (scheme_dir / f"{PROJECT_NAME}.xcscheme").write_text(render_scheme(ids), encoding="utf-8")

    swift_files = sorted(
        [path.relative_to(ios_root).as_posix() for path in app_root.rglob("*.swift")]
        + [path.relative_to(ios_root).as_posix() for path in tests_root.rglob("*.swift")]
    )
    print(f"Generated {project_dir.relative_to(ios_root)}")
    print(f"Inventoried {len(swift_files)} Swift source files")
    for path in swift_files:
        print(f"  {path}")
    print("Shared.framework is link-only; no static-framework copy phase was generated.")


if __name__ == "__main__":
    main()
