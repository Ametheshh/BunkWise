package com.example.mini_project;

public class VaultDoc {
    public int id;
    public String name;   // original file name (decrypted from DB)
    public String mime;
    public String file;   // random UUID name of the encrypted file in files/vault/

    public VaultDoc(int id, String name, String mime, String file) {
        this.id = id;
        this.name = name;
        this.mime = mime;
        this.file = file;
    }
}
