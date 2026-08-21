package org.example.dao;

import org.example.model.Member;
import org.example.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemberDAOImpl implements MemberDAO {

    @Override
    public Optional<Member> findById(String id) {
        String sql = "SELECT p.id, p.name, p.email, p.phone, m.unpaid_fines " +
                "FROM persons p JOIN members m ON p.id = m.person_id " +
                "WHERE p.id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToMember(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate kerkimit te anetarit: " + id, e);
        }
    }

    @Override
    public List<Member> findAll() {
        String sql = "SELECT p.id, p.name, p.email, p.phone, m.unpaid_fines " +
                "FROM persons p JOIN members m ON p.id = m.person_id";

        List<Member> members = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                members.add(mapRowToMember(rs));
            }
            return members;

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate leximit te te gjithe anetareve", e);
        }
    }

    @Override
    public void save(Member member) {
        // password_hash merr nje placeholder te pavlefshem si hash real BCrypt —
// personi krijohet, por s'mund te kycet derisa dikush (AuthService.hashPassword +
// UPDATE) t'i vendosi nje fjalekalim real. Njesoj si migrimi fillestar i M001/M002.
        String insertPerson = "INSERT INTO persons (id, name, email, phone, person_type, password_hash) " +
                "VALUES (?, ?, ?, ?, 'MEMBER', '$2a$10$PLACEHOLDER_TEMP_HASH_NOT_REAL_YET')";
        String insertMember = "INSERT INTO members (person_id, unpaid_fines) VALUES (?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt1 = conn.prepareStatement(insertPerson)) {
                    stmt1.setString(1, member.getId());
                    stmt1.setString(2, member.getName());
                    stmt1.setString(3, member.getEmail());
                    stmt1.setString(4, member.getPhone());
                    stmt1.executeUpdate();
                }

                try (PreparedStatement stmt2 = conn.prepareStatement(insertMember)) {
                    stmt2.setString(1, member.getId());
                    stmt2.setDouble(2, member.getUnpaidFees());
                    stmt2.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Gabim gjate ruajtjes se anetarit, ndryshimet u anuluan: " + member.getId(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate lidhjes me databazen", e);
        }
    }

    @Override
    public void update(Member member) {
        String sql = "UPDATE members SET unpaid_fines = ? WHERE person_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, member.getUnpaidFees());
            stmt.setString(2, member.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate perditesimit te anetarit: " + member.getId(), e);
        }
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM persons WHERE id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Gabim gjate fshirjes se anetarit: " + id, e);
        }
    }

    private Member mapRowToMember(ResultSet rs) throws SQLException {
        Member member = new Member(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone")
        );
        member.addFine(rs.getDouble("unpaid_fines"));
        return member;
    }
}