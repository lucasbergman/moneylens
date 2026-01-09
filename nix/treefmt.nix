{ pkgs, ... }:
let
  bestLineWidth = 88;
  clangFormatConfig = {
    IndentWidth = 4;
    ColumnLimit = bestLineWidth;
  };
  protolintConfig.lint = {
    rules.all_default = true;
    rules.remove = [
      "ENUMS_HAVE_COMMENT"
      "ENUM_FIELDS_HAVE_COMMENT"
    ];
    rules_option = {
      max_line_length.max_chars = bestLineWidth;
      indent.style = 4;
    };
  };
  clangFormatConfigFile = pkgs.writeText ".clang-format" (builtins.toJSON clangFormatConfig);
  protolintConfigFile = pkgs.writeText ".protolint.yaml" (builtins.toJSON protolintConfig);
in
{
  projectRootFile = "flake.nix";
  programs = {
    buildifier.enable = true;
    clang-format.enable = true;
    google-java-format = {
      enable = true;
      aospStyle = true; # 4-space indents
    };
    ktlint.enable = true;
    nixfmt.enable = true;
    prettier = {
      enable = true;
      settings = {
        printWidth = bestLineWidth;
        proseWrap = "always";
      };
    };
    protolint.enable = true;
  };
  settings.formatter = {
    clang-format = {
      includes = [ "*.proto" ];
      options = [ "-style=file:${clangFormatConfigFile}" ];
    };
    protolint = {
      options = [ "-config_path=${protolintConfigFile}" ];
    };
  };
}
