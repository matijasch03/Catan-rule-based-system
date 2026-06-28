package com.ftn.sbnz.service.repository;

import com.ftn.sbnz.model.Advice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdviceRepository extends JpaRepository<Advice, Integer> {
}
