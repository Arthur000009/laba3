package com.example.dostavka.services;

import com.example.dostavka.models.Route;
import com.example.dostavka.models.Shipment;
import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.models.Stop;
import com.example.dostavka.models.User;
import com.example.dostavka.repositories.RouteRepository;
import com.example.dostavka.repositories.ShipmentAssignmentRepository;
import com.example.dostavka.repositories.ShipmentRepository;
import com.example.dostavka.repositories.StopRepository;
import com.example.dostavka.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final ShipmentAssignmentRepository assignmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           UserRepository userRepository,
                           StopRepository stopRepository,
                           RouteRepository routeRepository,
                           ShipmentAssignmentRepository assignmentRepository) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    public Shipment getShipmentById(Long id) {
        return shipmentRepository.findById(id).orElse(null);
    }

    @Transactional
    public Shipment createShipment(Shipment shipment, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID " + senderId + " не найден"));
        shipment.setId(null);
        shipment.setSender(sender);
        shipment.setCreatedAt(LocalDateTime.now());
        shipment.setStatus("CREATED");
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment updateShipment(Long id, Shipment updates) {
        Shipment existing = shipmentRepository.findByIdWithLock(id).orElse(null);
        if (existing != null) {
            if (updates.getStatus() != null && !updates.getStatus().equals(existing.getStatus())) {
                throw new IllegalStateException("Прямое изменение статуса отправления через PATCH запрещено");
            }
            if (!"CREATED".equals(existing.getStatus())) {
                throw new IllegalStateException("Изменение адреса и веса отправления разрешено только в статусе CREATED");
            }
            if (updates.getAddress() != null) {
                existing.setAddress(updates.getAddress());
            }
            if (updates.getWeight() != null) {
                existing.setWeight(updates.getWeight());
            }
            return shipmentRepository.save(existing);
        }
        return null;
    }

    @Transactional
    public void deleteShipment(Long id) {
        Optional<ShipmentAssignment> preAssignment = assignmentRepository.findByShipmentId(id);
        if (preAssignment.isPresent()) {
            Long routeId = preAssignment.get().getStop().getRoute().getId();
            routeRepository.findByIdWithLock(routeId);
        }

        Shipment existing = shipmentRepository.findByIdWithLock(id).orElse(null);
        if (existing == null) {
            return;
        }
        if ("IN_TRANSIT".equals(existing.getStatus())) {
            throw new IllegalStateException("Невозможно удалить отправление, которое находится в пути (IN_TRANSIT)");
        }
        shipmentRepository.deleteById(id);
    }

    @Transactional
    public ShipmentAssignment transferShipment(Long shipmentId, Long newStopId) {
        Stop newStop = stopRepository.findById(newStopId)
                .orElseThrow(() -> new IllegalArgumentException("Целевая остановка с ID " + newStopId + " не найдена"));

        Shipment preShipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Отправление с ID " + shipmentId + " не найдено"));

        if (!"ASSIGNED".equals(preShipment.getStatus())) {
            throw new IllegalStateException("Перенос возможен только для отправлений в статусе ASSIGNED. Текущий статус: " + preShipment.getStatus());
        }

        ShipmentAssignment preAssignment = assignmentRepository.findByShipmentId(shipmentId)
                .orElseThrow(() -> new IllegalStateException("Для отправления не найдено активного назначения"));

        Long oldRouteId = preAssignment.getStop().getRoute().getId();
        Long newRouteId = newStop.getRoute().getId();

        Route oldRoute;
        Route newRoute;
        if (oldRouteId.equals(newRouteId)) {
            oldRoute = routeRepository.findByIdWithLock(oldRouteId)
                    .orElseThrow(() -> new IllegalArgumentException("Маршрут не найден"));
            newRoute = oldRoute;
        } else if (oldRouteId < newRouteId) {
            oldRoute = routeRepository.findByIdWithLock(oldRouteId)
                    .orElseThrow(() -> new IllegalArgumentException("Исходный маршрут не найден"));
            newRoute = routeRepository.findByIdWithLock(newRouteId)
                    .orElseThrow(() -> new IllegalArgumentException("Целевой маршрут не найден"));
        } else {
            newRoute = routeRepository.findByIdWithLock(newRouteId)
                    .orElseThrow(() -> new IllegalArgumentException("Целевой маршрут не найден"));
            oldRoute = routeRepository.findByIdWithLock(oldRouteId)
                    .orElseThrow(() -> new IllegalArgumentException("Исходный маршрут не найден"));
        }

        Shipment shipment = shipmentRepository.findByIdWithLock(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Отправление с ID " + shipmentId + " не найдено"));

        if (!"ASSIGNED".equals(shipment.getStatus())) {
            throw new IllegalStateException("Перенос возможен только для отправлений в статусе ASSIGNED. Текущий статус: " + shipment.getStatus());
        }

        ShipmentAssignment currentAssignment = assignmentRepository.findByShipmentId(shipmentId)
                .orElseThrow(() -> new IllegalStateException("Для отправления не найдено активного назначения"));

        Stop oldStop = currentAssignment.getStop();
        if (!oldStop.getRoute().getId().equals(oldRouteId)) {
            throw new IllegalStateException("Состояние назначения изменилось параллельным запросом, повторите операцию");
        }

        if (oldStop.getId().equals(newStopId)) {
            throw new IllegalStateException("Отправление уже назначено на данную остановку");
        }

        if (!newStop.getAddress().trim().equalsIgnoreCase(shipment.getAddress().trim())) {
            throw new IllegalStateException(
                    "Перенос невозможен: адрес отправления (" + shipment.getAddress() + ") не совпадает с адресом новой остановки (" + newStop.getAddress() + ")"
            );
        }

        if (!"PLANNED".equals(oldRoute.getStatus())) {
            throw new IllegalStateException("Нельзя перенести отправление: текущий маршрут уже находится в статусе " + oldRoute.getStatus());
        }

        if (!"PLANNED".equals(newRoute.getStatus())) {
            throw new IllegalStateException("Целевой маршрут недоступен для переноса, так как имеет статус " + newRoute.getStatus());
        }

        if (!oldRoute.getId().equals(newRoute.getId())) {
            double newRouteLoad = assignmentRepository.findByStopRouteId(newRoute.getId()).stream()
                    .mapToDouble(a -> a.getShipment().getWeight())
                    .sum();
            if (newRouteLoad + shipment.getWeight() > newRoute.getMaxWeight()) {
                throw new IllegalStateException(String.format(
                        "Перенос невозможен: превышен лимит веса целевого маршрута (занято: %.2f кг, посылка: %.2f кг, лимит: %.2f кг)",
                        newRouteLoad, shipment.getWeight(), newRoute.getMaxWeight()
                ));
            }
        }

        currentAssignment.setStop(newStop);
        return assignmentRepository.save(currentAssignment);
    }

    @Transactional
    public Shipment cancelShipment(Long shipmentId) {
        Optional<ShipmentAssignment> preAssignment = assignmentRepository.findByShipmentId(shipmentId);
        Route lockedRoute = null;
        if (preAssignment.isPresent()) {
            Long routeId = preAssignment.get().getStop().getRoute().getId();
            lockedRoute = routeRepository.findByIdWithLock(routeId)
                    .orElseThrow(() -> new IllegalArgumentException("Маршрут не найден"));
        }

        Shipment shipment = shipmentRepository.findByIdWithLock(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Отправление с ID " + shipmentId + " не найдено"));

        if ("DELIVERED".equals(shipment.getStatus())
                || "IN_TRANSIT".equals(shipment.getStatus())
                || "CANCELLED".equals(shipment.getStatus())) {
            throw new IllegalStateException("Невозможно отменить отправление в статусе " + shipment.getStatus());
        }

        Optional<ShipmentAssignment> currentAssignmentOpt = assignmentRepository.findByShipmentId(shipmentId);
        if (currentAssignmentOpt.isPresent()) {
            ShipmentAssignment currentAssignment = currentAssignmentOpt.get();
            Route currentRoute = currentAssignment.getStop().getRoute();
            if (lockedRoute == null || !lockedRoute.getId().equals(currentRoute.getId())) {
                throw new IllegalStateException("Состояние назначения изменилось параллельным запросом, повторите операцию");
            }
            if (!"PLANNED".equals(lockedRoute.getStatus())) {
                throw new IllegalStateException("Отмена невозможна: маршрут уже выполняется или завершен (статус " + lockedRoute.getStatus() + ")");
            }
            assignmentRepository.delete(currentAssignment);
        }

        shipment.setStatus("CANCELLED");
        return shipmentRepository.save(shipment);
    }
}