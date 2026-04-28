package com.eastcom.omagent.repository;

import com.eastcom.omagent.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    long deleteByIdAndUserId(String id, String userId);
}
