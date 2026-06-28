package com.ftn.sbnz.service.repository;

import com.ftn.sbnz.model.SynergyPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SynergyPairRepository extends JpaRepository<SynergyPair, Integer> {
}
