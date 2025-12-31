{
  pkgs,
  ...
}:
let
  jdk = pkgs.openjdk25;
  kotlin = pkgs.kotlin.override { jre = jdk; };
in
{
  default = pkgs.mkShell {
    buildInputs = [
      pkgs.bazel_8
      pkgs.bazel-buildtools

      jdk
      kotlin
    ];

    BAZEL_DO_NOT_DETECT_CPP_TOOLCHAIN = "1";
  };
}
