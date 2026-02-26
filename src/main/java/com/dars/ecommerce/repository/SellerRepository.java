package com.dars.ecommerce.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.dars.ecommerce.dto.Seller;

public interface SellerRepository extends JpaRepository<Seller, Integer> {

	boolean existsByEmail(String email);
	boolean existsByMobile(long mobile);
	Seller findByEmail(String email);

}