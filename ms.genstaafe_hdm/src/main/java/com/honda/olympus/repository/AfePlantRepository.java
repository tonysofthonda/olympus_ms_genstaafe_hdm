package com.honda.olympus.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.honda.olympus.dao.AfePlantEntity;

@Repository
public interface AfePlantRepository extends JpaRepository<AfePlantEntity, Long>{
	
	
	// QUERY4
		@Query("SELECT o FROM AfePlantEntity o WHERE o.id = :id ")
		List<AfePlantEntity> findPlantById(@Param("id") Long id);
	
}
