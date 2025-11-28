{
  description = "izumi build environment";

  # commit of branch `release-25.11` at the time. replace with `25.11` when it's released
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/166c9bd4e7f2a103b17dd2e23f6711bc7aa5dbbd";

  inputs.flake-utils.url = "github:numtide/flake-utils";

  inputs.mudyla.url = "github:7mind/mudyla";
  inputs.mudyla.inputs.nixpkgs.follows = "nixpkgs";

  outputs =
    { self
    , nixpkgs
    , flake-utils
    , mudyla
    ,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs.buildPackages; [
            ncurses

            coursier
            sbt

            nodejs
            nodePackages.npm

            gitMinimal
            openssh

            mudyla.packages.${system}.default

            docker
            scala-cli
          ];

          shellHook = ''
            export JDK11=${pkgs.jdk11_headless}
            export JDK17=${pkgs.jdk17_headless}
            export JDK21=${pkgs.jdk21_headless}
            export JDK25=${pkgs.jdk25_headless}
          '';
        };
      }
    );
}
