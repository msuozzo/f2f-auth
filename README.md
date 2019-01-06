Face-to-Face Auth
=======

Contents:
* **paper/**: The report introducing and describing F2FAuth.
* **F2FAuth/**: The Android app client.
* **server.go**: The authentication server.
* **gen_tls_cert.bash**: A script compatible with LibreSSL 2.2.7 to generate
  self-signed root TLS certs with custom SubjectAlternateName fields that
  support IP-based validation as opposed to CommonName validation (Android
  doesn't recognize IP addresses in the CommonName field).

The general development flow is:
* Generate a root TLS cert for the IP that will serve the auth server.
* Include the cert in the App.
* Start the server on the aforementioned IP.
* Flash the app to your device(s).
* Start the app and provision your device(s) using this new IP.

For further details, see the F2FAuthApp README and, primarily, the paper.
