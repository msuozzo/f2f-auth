// The authentication server for F2FAuth.
//
// To run server:
//   go run server.go -addr=":8080" -dbPath=/tmp/auth.db -key=/tmp/local_key.pem -cert=/tmp/local_cert.pem
package main

import (
	"bytes"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"math/rand"
	"net"
	"net/http"
	"strings"

	"github.com/dgrijalva/jwt-go"
	"github.com/jinzhu/gorm"
	_ "github.com/jinzhu/gorm/dialects/sqlite"
)

var certFile = flag.String("cert", "", "")
var keyFile = flag.String("key", "", "")
var clientCertCaFile = flag.String("clientCertCa", "", "")
var adminClientCertCaFile = flag.String("adminClientCertCa", "", "")
var addr = flag.String("addr", "[::1]:8080", "Port to serve on")
var dbPath = flag.String("dbPath", "/tmp/auth.db", "Sqlite database file to use")
var seed = flag.Int64("seed", 20, "Seed to use for the prng")
var realm = flag.String("realm", "foo", "Realm used for this auth server")

// TODO: This would likely be a blob storage service in a real version.
var baseImgPath = flag.String("baseImgPath", "/tmp/images", "Base path used to store and search for user images")

var (
	db   *gorm.DB
	prng *rand.Rand
)

type Device struct {
	gorm.Model
	Name                 string
	Realm                string
	PublicKey            string
	PublicKeyFingerprint string
	DisplayName          string
	ImageRelPath         string
}

func SayHello(w http.ResponseWriter, r *http.Request) {
	io.WriteString(w, "Hello, world!\n")
}

func GetRealm(w http.ResponseWriter, r *http.Request) {
	io.WriteString(w, *realm)
}

func validateToken(s string) error {
	c := jwt.StandardClaims{}
	_, err := jwt.ParseWithClaims(s, &c, func(token *jwt.Token) (interface{}, error) {
		// since we only use the one private key to sign the tokens,
		// we also only use its public counter part to verify
		return []byte("shhhh"), nil
	})
	if err != nil {
		return err
	}
	if err = c.Valid(); err != nil {
		return err
	}
	return nil
}

func authenticate(r *http.Request) error {
	a := r.Header.Get("Authentication")
	if strings.HasPrefix(a, "Bearer ") {
		return validateToken(a[len("Bearer "):])
	}
	return errors.New("No authentication header provided")
}

// TODO: set cookie and/or cache decision
func ProxyRequest(w http.ResponseWriter, r *http.Request) {
	if err := authenticate(r); err != nil {
		log.Println(err)
		http.Redirect(w, r, "/refresh", http.StatusFound)
	} else {
		if err := r.ParseForm(); err != nil {
			log.Println(err)
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		} else {
			n := r.Form.Get("next")
			// TODO: validate next uri. require same domain?
			if len(n) != 0 {
				http.Redirect(w, r, n, http.StatusFound)
			} else {
				http.Error(w, http.StatusText(http.StatusOK), http.StatusOK)
			}
		}
	}
}

func RefreshToken(w http.ResponseWriter, r *http.Request) {
	// TODO: pull from the client cert instead of query param
	if err := r.ParseForm(); err != nil {
		log.Println(err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	name := r.Form.Get("name")
	ts := r.Form.Get("ts")
	sig := r.Form.Get("sig")
	pname := r.Form.Get("peerName")
	pts := r.Form.Get("peerTs")
	psig := r.Form.Get("peerSig")
	// Don't permit a single user to construct a valid credential.
	if name == pname {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return
	}
	if verifyAll(w, name, pname, ts, pts, sig, psig) {
		tok_enc := base64.RawURLEncoding.EncodeToString([]byte(name + "|" + ts + "|" + sig + "|" + pname + "|" + pts + "|" + psig))
		io.WriteString(w, tok_enc)
	}
	return
}

func verifyAll(w http.ResponseWriter, name string, pname string, ts string, pts string, sig string, psig string) bool {
	// Validate primary sig
	sig_enc, err := base64.URLEncoding.DecodeString(sig)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return false
	}
	d, err := getDevice(name)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return false
	}
	pub, err := getPubKey(d.PublicKey)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return false
	}
	log.Println("Verifying '" + name + "|" + ts + "' for " + sig + " with " + d.PublicKeyFingerprint)
	if !verify(pub, "auth1|"+name+"|"+ts, sig_enc) {
		log.Println("Failed to verify primary")
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return false
	}
	psig_enc, err := base64.URLEncoding.DecodeString(psig)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return false
	}
	pd, err := getDevice(pname)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return false
	}
	if d.Realm != pd.Realm {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return false
	}
	ppub, err := getPubKey(pd.PublicKey)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return false
	}
	if !verify(ppub, "auth2|"+name+"|"+ts+"|"+sig+"|"+pname+"|"+pts, psig_enc) {
		log.Println("Failed to verify peer")
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return false
	}
	log.Println("VERIFIED!!")
	return true
}

func verify(pub *rsa.PublicKey, payload string, sig_enc []byte) bool {
	hashed := sha256.Sum256([]byte(payload))
	err := rsa.VerifyPKCS1v15(pub, crypto.SHA256, hashed[:], sig_enc)
	if err != nil {
		log.Println(err)
		return false
	}
	return true
}

func Test(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		log.Println(err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	a := r.Header.Get("Authentication")
	tok_enc, err := base64.RawURLEncoding.DecodeString(a)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	lst := strings.Split(string(tok_enc), "|")
	if !verifyAll(w, lst[0], lst[3], lst[1], lst[4], lst[2], lst[5]) {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
	}
	return
}

func isAdmin(clientCertFingerprint string) bool {
	// TODO: Implement client cert admin check
	return true
}

func isSameUser(clientCertFingerprint string, name string) bool {
	// TODO: implement client cert identity check
	return true
}

func UploadImage(w http.ResponseWriter, r *http.Request) {
	// TODO: implement client cert admin check
	if !isAdmin("") {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return
	}
	if err := r.ParseForm(); err != nil {
		log.Println(err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	n := r.Form.Get("name")
	if len(n) == 0 {
		http.Error(w, "Must provide a 'name' parameter", http.StatusBadRequest)
		return
	}
	d, err := getDevice(n)
	if err != nil {
		log.Println(err)
		if db.RecordNotFound() {
			http.Error(w, http.StatusText(http.StatusNotFound), http.StatusNotFound)
		} else {
			http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		}
		return
	}
	var buf bytes.Buffer
	file, header, err := r.FormFile("file")
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	defer file.Close()
	io.Copy(&buf, file)
	name := strings.Split(header.Filename, ".")
	fname := n + "." + name[len(name)-1]
	path := *baseImgPath + "/" + fname
	err = ioutil.WriteFile(path, buf.Bytes(), 0644)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	// Update the device entity.
	d.ImageRelPath = fname
	res := db.Save(&d)
	if res.Error != nil {
		log.Println(res.Error)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
}

func GetImage(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		log.Println(err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	n := r.Form.Get("name")
	if len(n) == 0 {
		http.Error(w, "Must provide a 'name' parameter", http.StatusBadRequest)
		return
	}
	d, err := getDevice(n)
	if err != nil {
		log.Println(err)
		if db.RecordNotFound() {
			http.Error(w, http.StatusText(http.StatusNotFound), http.StatusNotFound)
		} else {
			http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		}
		return
	}
	dat, err := ioutil.ReadFile(*baseImgPath + "/" + d.ImageRelPath)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	io.WriteString(w, string(dat))
}

func getRandHash() (string, error) {
	v := make([]byte, sha256.Size)
	_, err := prng.Read(v)
	if err != nil {
		return "", err
	}
	h := sha256.Sum256(v)
	return fmt.Sprintf("%#x", h)[2:], nil
}

func ProvisionNewDevice(w http.ResponseWriter, r *http.Request) {
	// TODO: implement client cert admin check
	if !isAdmin("") {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return
	}
	if r.Method == "GET" {
		http.Error(w, "No GET handler", http.StatusBadRequest)
		return
	}
	n, err := getRandHash()
	if err != nil {
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	d := Device{Name: n, Realm: *realm}
	res := db.Create(&d)
	if res.Error != nil {
		log.Println(res.Error)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	log.Println(d)
	m, err := json.Marshal(d)
	if err != nil {
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	log.Println(m)
	io.WriteString(w, string(m))
}

func FinalizeDevice(w http.ResponseWriter, r *http.Request) {
	// TODO: implement client cert admin check
	if !isAdmin("") {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return
	}
	if err := r.ParseForm(); err != nil {
		log.Println(err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	n := r.Form.Get("name")
	if len(n) == 0 {
		http.Error(w, "Must provide a 'name' parameter", http.StatusBadRequest)
		return
	}
	// TODO: Add Display Name
	// dn := r.Form.Get("displayName")
	// if len(n) == 0 {
	// 	http.Error(w, "Must provide a 'displayName' parameter", http.StatusBadRequest)
	// 	return
	// }
	pk := r.Form.Get("pk")
	if len(n) == 0 {
		http.Error(w, "Must provide a 'pk' parameter", http.StatusBadRequest)
		return
	}
	d, err := getDevice(n)
	if err != nil {
		log.Println(err)
		if db.RecordNotFound() {
			http.Error(w, http.StatusText(http.StatusNotFound), http.StatusNotFound)
		} else {
			http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		}
		return
	}
	// TODO: Parse pk to extract fingerprint
	//x509.ParsePKCS1PublicKey
	log.Println(pk)
	fp, err := getFingerprint(pk)
	if err != nil {
		log.Println(err)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	d.PublicKey = pk
	d.PublicKeyFingerprint = fp
	//d.DisplayName = dn

	// TODO: Generate a client cert here with the device_id.
	res := db.Save(&d)
	if res.Error != nil {
		log.Println(res.Error)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	m, err := json.Marshal(d)
	if err != nil {
		log.Println(res.Error)
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	log.Println(m)
	io.WriteString(w, string(m))
}

func getFingerprint(b64DerPk string) (string, error) {
	derPk, err := base64.URLEncoding.DecodeString(b64DerPk)
	if err != nil {
		return "", err
	}
	hash := sha256.Sum256([]byte(derPk))
	return hex.EncodeToString(hash[:]), nil
}

func getPubKey(b64DerPk string) (*rsa.PublicKey, error) {
	pk_enc, err := base64.URLEncoding.DecodeString(b64DerPk)
	if err != nil {
		return nil, err
	}
	pub, err := x509.ParsePKIXPublicKey(pk_enc)
	if err != nil {
		return nil, err
	}
	switch pub := pub.(type) {
	case *rsa.PublicKey:
		return pub, nil
	default:
		return nil, fmt.Errorf("unknown type of public key")
	}
}

func getDevice(name string) (Device, error) {
	var d Device
	res := db.First(&d, "name = ?", name)
	if res.Error != nil {
		return d, res.Error
	}
	return d, nil
}

func GetDevice(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		log.Println(err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	n := r.Form.Get("name")
	if len(n) == 0 {
		http.Error(w, "Must provide a 'name' parameter", http.StatusBadRequest)
		return
	}
	// TODO: implement client cert checks
	if !isAdmin("") && !isSameUser("", n) {
		http.Error(w, http.StatusText(http.StatusForbidden), http.StatusForbidden)
		return
	}
	d, err := getDevice(n)
	if err != nil {
		log.Println(err)
		if db.RecordNotFound() {
			http.Error(w, http.StatusText(http.StatusNotFound), http.StatusNotFound)
		} else {
			http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		}
		return
	}
	log.Println(d)
	m, err := json.Marshal(d)
	if err != nil {
		http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
		return
	}
	io.WriteString(w, string(m))
}

func initDb() {
	var err error
	db, err = gorm.Open("sqlite3", *dbPath)
	if err != nil {
		log.Fatal(err)
	}
	db.AutoMigrate(&Device{})

	res := db.Create(&Device{Name: "L1212"})
	if res.Error != nil {
		log.Println(res.Error)
	}
}

func initPrng() {
	s2 := rand.NewSource(*seed)
	prng = rand.New(s2)
}

func initHandlers() {
	http.HandleFunc("/", ProxyRequest)
	http.HandleFunc("/ack", func(w http.ResponseWriter, r *http.Request) {
		log.Println("GOT A REQUEST!!!!!!!!!!!")
		log.Println(r.Header)
	})
	http.HandleFunc("/hello", SayHello)
	http.HandleFunc("/realm", GetRealm)
	http.HandleFunc("/refresh", RefreshToken)
	http.HandleFunc("/provision", ProvisionNewDevice)
	http.HandleFunc("/provision/finalize", FinalizeDevice)
	http.HandleFunc("/devices", GetDevice)
	http.HandleFunc("/upload", UploadImage)
	http.HandleFunc("/images", GetImage)
	http.HandleFunc("/test", Test)
}

func main() {
	flag.Parse()

	initPrng()
	initDb()
	defer db.Close()
	initHandlers()

	tlsConfig := &tls.Config{
		ClientAuth: tls.NoClientCert,
	}
	if *clientCertCaFile != "" {
		caCertPool := x509.NewCertPool()
		caCert, err := ioutil.ReadFile(*clientCertCaFile)
		if err != nil {
			log.Fatal(err)
		}
		caCertPool.AppendCertsFromPEM(caCert)
		tlsConfig.ClientCAs = caCertPool
		tlsConfig.ClientAuth = tls.RequireAndVerifyClientCert
	}
	tlsConfig.BuildNameToCertificate()

	serv := &http.Server{
		Addr:      *addr,
		TLSConfig: tlsConfig,
	}

	l, err := net.Listen("tcp", serv.Addr)
	if err != nil {
		log.Fatal(err)
	}
	if *certFile != "" && *keyFile != "" {
		log.Fatal(serv.ServeTLS(l, *certFile, *keyFile))
	} else {
		log.Fatal(serv.Serve(l))
	}
}
