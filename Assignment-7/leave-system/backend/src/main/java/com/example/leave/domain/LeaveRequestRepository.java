package com.example.leave.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeEmailOrderByCreatedAtDesc(String employeeEmail);
    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE LeaveRequest r SET r.processInstanceKey = :key WHERE r.id = :id")
    void updateProcessInstanceKey(@Param("id") Long id, @Param("key") Long key);
}