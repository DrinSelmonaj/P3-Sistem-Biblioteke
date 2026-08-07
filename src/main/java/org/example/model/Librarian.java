package org.example.model;

public class Librarian extends Person {
    private String employeeCode;

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
