package com.dars.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dars.ecommerce.dto.Item;

public interface ItemRepository extends JpaRepository<Item, Integer> {

}