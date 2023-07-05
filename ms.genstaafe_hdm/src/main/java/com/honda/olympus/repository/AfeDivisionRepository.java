package com.honda.olympus.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.honda.olympus.dao.AfeDivisionEntity;

@Repository
public interface AfeDivisionRepository extends JpaRepository<AfeDivisionEntity, Long>{

	
	// QUERY4
		@Query("SELECT o FROM AfeDivisionEntity o WHERE o.id = :id ")
		List<AfeDivisionEntity> findDivisionById(@Param("id") Long id);
}
