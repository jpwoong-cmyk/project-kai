// Kai launcher: one executable that carries its own Java runtime and kai.jar.
// On first start it unpacks them next to itself, into <folder of this program>/.kai-runtime/<hash>,
// so deleting the Kai folder removes everything. The leading dot hides it in Finder and makes
// Kai's own scanner skip it. Then it runs:
//   java -jar kai.jar --config=<folder of this program>/kai.properties
// A new build has a new hash, so it unpacks fresh and never mixes versions;
// the first start of a new build then deletes the older versions' folders.
package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	_ "embed"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
)

// payload.tar.gz holds jre/ and kai.jar; build.sh creates it before go build.
//
//go:embed payload.tar.gz
var payload []byte

func main() {
	code, err := run()
	if err != nil {
		fmt.Fprintln(os.Stderr, "Kai could not start:", err)
		fmt.Fprintln(os.Stderr, "Press Enter to close this window.")
		fmt.Scanln()
		code = 1
	}
	os.Exit(code)
}

func run() (int, error) {
	exe, err := os.Executable()
	if err != nil {
		return 0, err
	}
	if exe, err = filepath.EvalSymlinks(exe); err != nil {
		return 0, err
	}
	home := filepath.Dir(exe)
	dir, err := unpack(filepath.Join(home, ".kai-runtime"))
	if err != nil {
		return 0, fmt.Errorf("unpacking Java failed (is the Kai folder writable and is there free disk space?): %w", err)
	}

	java := filepath.Join(dir, "jre", "bin", "java")
	if runtime.GOOS == "windows" {
		java += ".exe"
	}
	args := []string{"-jar", filepath.Join(dir, "kai.jar")}
	if !hasConfig(os.Args[1:]) {
		args = append(args, "--config="+filepath.Join(home, "kai.properties"))
	}
	args = append(args, os.Args[1:]...)

	cmd := exec.Command(java, args...)
	cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	signal.Ignore(os.Interrupt) // Ctrl+C reaches Java too; let it shut down, then exit with its code
	err = cmd.Run()
	var exit *exec.ExitError
	if errors.As(err, &exit) {
		return exit.ExitCode(), nil
	}
	return 0, err
}

func hasConfig(args []string) bool {
	for _, a := range args {
		if strings.HasPrefix(a, "--config=") {
			return true
		}
	}
	return false
}

// unpack returns the folder with jre/ and kai.jar, unpacking only if it is not there yet.
// It unpacks into a temp folder and renames it at the end, so a half-finished
// unpack (power cut, two starts at once) is never used.
func unpack(base string) (string, error) {
	sum := sha256.Sum256(payload)
	dir := filepath.Join(base, hex.EncodeToString(sum[:6]))
	if _, err := os.Stat(dir); err == nil {
		return dir, nil
	}
	if err := os.MkdirAll(filepath.Dir(dir), 0o755); err != nil {
		return "", err
	}
	tmp, err := os.MkdirTemp(filepath.Dir(dir), "unpack-")
	if err != nil {
		return "", err
	}
	defer os.RemoveAll(tmp) // no-op after a successful rename
	if err := untar(tmp); err != nil {
		return "", err
	}
	if err := os.Rename(tmp, dir); err != nil {
		if _, statErr := os.Stat(dir); statErr == nil {
			return dir, nil // another start finished first
		}
		return "", err
	}
	removeOld(dir)
	return dir, nil
}

// removeOld deletes the folders of earlier versions, so users never have to clean up.
// Each is renamed to trash-<hash> first: on Windows that fails while the old version is
// still running (its files are locked), so it is left for next time; and a half-finished
// delete never sits under a name that a launcher would use. Best effort: errors are ignored.
func removeOld(keep string) {
	parent := filepath.Dir(keep)
	entries, _ := os.ReadDir(parent)
	for _, e := range entries {
		name := e.Name()
		path := filepath.Join(parent, name)
		switch {
		case path == keep || strings.HasPrefix(name, "unpack-"): // current, or another start unpacking
		case strings.HasPrefix(name, "trash-"):
			os.RemoveAll(path)
		default:
			trash := filepath.Join(parent, "trash-"+name)
			if os.Rename(path, trash) == nil {
				os.RemoveAll(trash)
			}
		}
	}
}

func untar(dst string) error {
	gz, err := gzip.NewReader(bytes.NewReader(payload))
	if err != nil {
		return err
	}
	tr := tar.NewReader(gz)
	for {
		h, err := tr.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
		path := filepath.Join(dst, h.Name)
		switch h.Typeflag {
		case tar.TypeDir:
			err = os.MkdirAll(path, 0o755)
		case tar.TypeReg:
			err = write(path, tr, h.FileInfo().Mode().Perm()|0o200) // keep exec bits; stay deletable
		case tar.TypeLink: // tar -h stores repeated symlink targets (jre/legal) as hard links
			err = os.Link(filepath.Join(dst, h.Linkname), path)
		}
		if err != nil {
			return err
		}
	}
}

func write(path string, r io.Reader, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}
	_, err = io.Copy(f, r)
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	return err
}
