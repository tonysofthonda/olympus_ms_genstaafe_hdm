package com.honda.olympus.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.honda.olympus.dao.AfeEventStatusHistoryEntity;

@Repository
public interface AfeEventStatusHistoryRepository extends JpaRepository<AfeEventStatusHistoryEntity, Long>{

	// QUERY3
		@Query("SELECT o FROM AfeEventStatusHistoryEntity o WHERE o.afeEventStatusId = :afeEventStatusId  order by asc o.id ")
		List<AfeEventStatusHistoryEntity> findAllByCode(@Param("afeEventStatusId") Long afeEventStatusId);
	
}
