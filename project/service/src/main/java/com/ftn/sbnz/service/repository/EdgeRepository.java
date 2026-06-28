package com.ftn.sbnz.service.repository;

import com.ftn.sbnz.model.Edge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EdgeRepository extends JpaRepository<Edge, Integer> {
}
