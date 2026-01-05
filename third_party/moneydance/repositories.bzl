load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def _moneydance_deps_impl(module_ctx):
    http_archive(
        name = "moneydance_devkit",
        build_file = "//third_party/moneydance:BUILD.moneydance.bazel",
        integrity = "sha256-AxTwSGPukkot0SsDg3TJA0JTPk54dgfjAAAKdqzTI4I=",
        strip_prefix = "moneydance-devkit-5.1",
        urls = ["https://infinitekind.com/dev/moneydance-devkit-5.1.tar.gz"],
    )

moneydance_deps = module_extension(
    implementation = _moneydance_deps_impl,
)
