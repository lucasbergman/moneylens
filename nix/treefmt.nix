{ pkgs, ... }:
let
  bestLineWidth = 88;
in
{
  projectRootFile = "flake.nix";
  programs = {
    buildifier.enable = true;
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
  };
}
