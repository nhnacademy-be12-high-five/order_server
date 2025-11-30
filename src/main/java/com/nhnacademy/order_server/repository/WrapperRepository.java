package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Wrapper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WrapperRepository extends JpaRepository<Wrapper,Long> {

    List<Wrapper> findByIsAvailableTrue();
}
