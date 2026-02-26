package com.dars.ecommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dars.ecommerce.dto.Customer;
import com.dars.ecommerce.dto.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Integer> {

	List<CustomerOrder> findByCustomerAndPaymentIdIsNotNull(Customer customer);
    
}