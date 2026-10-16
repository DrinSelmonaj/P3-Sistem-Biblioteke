package org.example.model;

/**
 * Personi ne sistem — baza e {@link Member} dhe {@link Librarian}. Ne DB ruhet me
 * Class Table Inheritance: {@code persons} + {@code members}/{@code librarians}.
 */
public abstract class Person {
    private String id;
    private String name;
    private String phone;
    private String email;

    public Person(String id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    /**
     * Kontrolli polimorfik i autorizimit — service-t e pyesin kete, jo {@code instanceof}.
     *
     * @return true per bibliotekaret (inventar, gjoba, raporte), false per anetaret
     */
    public abstract boolean canManageInventory();

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}
