# NOTE: Once the cert is generated, it must be packaged in the android app.
# See README in F2FAuthApp for details.
commonname=${1:="127.0.0.1"}  # TODO: replace with the IP/common name to use for the script
basedir="/tmp"
keyname="local_key"
certname="local_cert"

country='US'
state='New York'
locality='New York'
keypath="${basedir}/${keyname}.pem"
certpath="${basedir}/${certname}.pem"

openssl genrsa -out "${keypath}" 2048
csrpath="${basedir}/${certname}.csr"
openssl req \
  -new \
  -key "${keypath}" \
  -out "${csrpath}" \
  -config <(cat <<-EOF
[req]
prompt = no
md = sha256
req_extensions = req_ext
distinguished_name = dn

[ dn ]
C=${country}
ST=${state}
L=${locality}
CN=${commonname}

[ req_ext ]
subjectAltName=@alt_names

[ alt_names ]
IP.1=${commonname}
EOF
)

openssl req -text -noout -in "${csrpath}"
openssl x509 -req \
  -sha256 \
  -days 365 \
  -in "${csrpath}" \
  -signkey "${keypath}" \
  -out "${certpath}" \
  -extensions req_ext \
  -extfile <(cat <<-EOF
[ req_ext ]
subjectAltName=@alt_names

[ alt_names ]
IP.1=${commonname}
EOF
)
openssl x509 -text -noout -in "${certpath}"

# Copy new cert into app.
cp "${certpath}" "./F2FAuth/app/src/main/res/raw/tls_cert.pem"
