echo "KEY"
echo $encrypted_0429e73c206c_key | openssl enc -aes-256-cbc -a -k $ECHOKEY
echo "IV"
echo $encrypted_0429e73c206c_iv | openssl enc -aes-256-cbc -a -k $ECHOKEY
