{
  description = "izumi build environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/24.05";

  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs =
    { self
    , nixpkgs
    , flake-utils
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

            docker
            scala-cli
          ];

          shellHook = ''
            export JDK11=${pkgs.jdk11_headless}
            export JDK17=${pkgs.jdk17_headless}
            export JDK22=${pkgs.jdk22_headless}
          '';
        };
      }
    );
}
