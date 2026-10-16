package org.example.model;

/**
 * Bibliotekar: menaxhon inventarin, huazimet ne emer te anetareve, gjobat, raportet
 * dhe llogarite e bibliotekareve te tjere.
 */
public class Librarian extends Person {
    private String employeeCode;

    /**
     * @param id           username-i
     * @param name         emri i plote
     * @param email        adresa email
     * @param phone        telefoni, ose null
     * @param employeeCode kodi unik i punonjesit
     */
    public Librarian(String id, String name, String email, String phone, String employeeCode) {
        super(id, name, email, phone);
        this.employeeCode = employeeCode;
    }

    @Override
    public boolean canManageInventory() {
        return true;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }
}
