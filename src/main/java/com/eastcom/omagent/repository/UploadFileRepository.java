package com.eastcom.omagent.repository;

import com.eastcom.omagent.entity.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadFileRepository extends JpaRepository<UploadFile, String> {

    List<UploadFile> findByUserIdOrderByCreatedAtDesc(String userId);

    List<UploadFile> findByKnowledgeTypeAndVectorizedFalse(String knowledgeType);

    long countByKnowledgeType(String knowledgeType);
}
