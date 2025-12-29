{
  description = "Automatically make transactions for the Moneydance ledger";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-25.11";
    flake-utils.url = "github:numtide/flake-utils";
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      treefmt-nix,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };
        treefmt = treefmt-nix.lib.evalModule pkgs ./nix/treefmt.nix;
      in
      {
        formatter = treefmt.config.build.wrapper;
        packages = import ./pkgs { inherit pkgs; };
        devShells = import ./nix/shells.nix { inherit pkgs; };

        checks = {
          formatting = treefmt.config.build.check self;
        };
      }
    );
}
