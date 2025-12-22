package com.nhnacademy.order_server.repository;

import com.nhnacademy.order_server.entity.Wrapper;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrapperRepository extends JpaRepository<Wrapper,Long> {

    List<Wrapper> findByIsAvailableTrue();
}
