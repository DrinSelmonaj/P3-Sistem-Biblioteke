package org.example.dao;

import org.example.model.Member;
import java.util.List;
import java.util.Optional;

public interface MemberDAO {
    Optional<Member> findById(String id);
    List<Member> findAll();
    void save(Member member);
    void update(Member member);
    void delete(String id);
}