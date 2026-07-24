package com.ecocode.scheduler.repository;

import com.ecocode.scheduler.model.TaskRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Standard Spring Data JPA repository. Extending JpaRepository gives us
 * save(), findById(), findAll(), delete(), etc. for free - backed by
 * the MySQL "tasks" table.
 */
public interface TaskRepository extends JpaRepository<TaskRecord, String> {
}
