package com.ftn.sbnz.service.repository;

import com.ftn.sbnz.model.Hexagon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HexagonRepository extends JpaRepository<Hexagon, Integer> {
}
