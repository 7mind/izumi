#!/bin/bash -e

EMAIL=pshirshov@gmail.com
PASSPHRASE=$(uuidgen)
SECRETS=./.secrets
GPGHOME=$SECRETS/gnupg.home
GPGTARGET=$SECRETS/gnupg
LOCALSBT=$SECRETS/local.sbt
GPGTMP=/tmp/gpginput
PUBRING=$GPGTARGET/pubring.gpg
SECRING=$GPGTARGET/secring.gpg

echo "Passphrase: $PASSPHRASE"
rm -rf $GPGTARGET
rm -rf $GPGHOME
mkdir -p $GPGTARGET
mkdir -p $GPGHOME
chmod 700 $GPGHOME

cat >$GPGTMP <<EOF
     %echo Generating a basic OpenPGP key
     Key-Type: RSA
     Key-Length: 2048
     Key-Usage: encrypt,sign,auth
     Name-Real: Pavel Shirshov
     Name-Comment: izumi-r2 sonatype key
     Name-Email: $EMAIL
     Expire-Date: 0
     Passphrase: $PASSPHRASE
     %commit
     %echo done
EOF

#     Subkey-Type: RSA
#     Subkey-Length: 2048
#     Subkey-Usage: encrypt,sign,auth

gpg --homedir $GPGHOME --batch --full-generate-key $GPGTMP
rm -f $GPGTMP

# export
gpg --homedir $GPGHOME --list-keys --keyid-format short
gpg --homedir $GPGHOME --batch --yes --passphrase $PASSPHRASE --pinentry-mode loopback --export-secret-keys  > $SECRING
gpg --homedir $GPGHOME --batch --yes --passphrase $PASSPHRASE --pinentry-mode loopback --export > $PUBRING

#sbt shim
rm -f local.sbt
cat >$LOCALSBT <<EOF
pgpPassphrase := Some("$PASSPHRASE".toCharArray)
pgpSecretRing := file("$SECRING")
pgpPublicRing := file("$PUBRING")
useGpg := false
EOF
ln -s $LOCALSBT .

# publish
for fpr in $(gpg --homedir $GPGHOME --list-keys --with-colons  | awk -F: '/fpr:/ {print $10}' | sort -u); do gpg --homedir $GPGHOME --send-keys --keyserver ipv4.pool.sks-keyservers.net $fpr; done
for fpr in $(gpg --homedir $GPGHOME --list-keys --with-colons  | awk -F: '/fpr:/ {print $10}' | sort -u); do gpg --homedir $GPGHOME --send-keys --keyserver keyserver.ubuntu.com $fpr; done
