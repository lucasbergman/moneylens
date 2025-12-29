{ pkgs, ... }:
{
  projectRootFile = "flake.nix";
  programs = {
    nixfmt.enable = true;
    prettier = {
      enable = true;
      settings = {
        printWidth = 88;
        proseWrap = "always";
      };
    };
  };
}
