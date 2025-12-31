load("@rules_nixpkgs_cc//:cc.bzl", "nixpkgs_cc_configure")
load("@rules_nixpkgs_java//:java.bzl", "nixpkgs_java_configure")

def _toolchains_configure_impl(module_ctx):
    nixpkgs_java_configure(
        name = "nixpkgs_java_runtime",
        attribute_path = "jdk25.home",
        repository = "@nixpkgs",
        toolchain = True,
        toolchain_name = "nixpkgs_java",
        toolchain_version = "25",
        register = False,
    )

    nixpkgs_cc_configure(
        name = "nixpkgs_cc",
        repository = "@nixpkgs",
        register = False,
        attribute_path = "clang_21",
        cc_std = "c++17",
    )

toolchains_configure = module_extension(
    implementation = _toolchains_configure_impl,
)
