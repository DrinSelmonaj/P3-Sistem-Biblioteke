package org.example.model;

public abstract class Person {
    private String id;
    private String name;
    private String phone;
    private String email;

    public Person(String id, String email, String name, String phone) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.phone = phone;
    }

    public abstract boolean canManageInventory();

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}
