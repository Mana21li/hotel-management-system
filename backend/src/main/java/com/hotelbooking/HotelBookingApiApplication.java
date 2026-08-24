package com.hotelbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Public HTTP edge on {@code :8080}. Controllers keep the original paths;
 * hotel, search, and booking work is forwarded to extracted services.
 */
@SpringBootApplication
public class HotelBookingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HotelBookingApiApplication.class, args);
	}

}
