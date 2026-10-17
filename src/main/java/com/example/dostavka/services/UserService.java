package com.example.dostavka.services;

import com.example.dostavka.models.Shipment;
import com.example.dostavka.models.User;
import com.example.dostavka.repositories.ShipmentRepository;
import com.example.dostavka.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShipmentRepository shipmentRepository;

    public UserService(UserRepository userRepository, ShipmentRepository shipmentRepository) {
        this.userRepository = userRepository;
        this.shipmentRepository = shipmentRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Transactional
    public User createUser(User user) {
        user.setId(null);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, User userUpdates) {
        User existingUser = userRepository.findById(id).orElse(null);
        if (existingUser != null) {
            if (userUpdates.getName() != null) {
                existingUser.setName(userUpdates.getName());
            }
            if (userUpdates.getEmail() != null) {
                existingUser.setEmail(userUpdates.getEmail());
            }
            return userRepository.save(existingUser);
        }
        return null;
    }

    @Transactional
    public void deleteUser(Long id) {
        User existing = userRepository.findById(id).orElse(null);
        if (existing == null) {
            return;
        }
        List<Shipment> userShipments = shipmentRepository.findBySenderId(id);
        boolean hasActiveShipments = userShipments.stream()
                .anyMatch(s -> !"DELIVERED".equals(s.getStatus()) && !"CANCELLED".equals(s.getStatus()));
        if (hasActiveShipments) {
            throw new IllegalStateException(
                    "Невозможно удалить пользователя: у него есть активные отправления (не в статусах DELIVERED или CANCELLED)"
            );
        }
        userRepository.deleteById(id);
    }
}