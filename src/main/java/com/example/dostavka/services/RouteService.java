package com.example.dostavka.services;

import com.example.dostavka.models.Route;
import com.example.dostavka.models.Shipment;
import com.example.dostavka.models.ShipmentAssignment;
import com.example.dostavka.models.Stop;
import com.example.dostavka.repositories.RouteRepository;
import com.example.dostavka.repositories.ShipmentAssignmentRepository;
import com.example.dostavka.repositories.ShipmentRepository;
import com.example.dostavka.repositories.StopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentAssignmentRepository assignmentRepository;

    public RouteService(RouteRepository routeRepository,
                        StopRepository stopRepository,
                        ShipmentRepository shipmentRepository,
                        ShipmentAssignmentRepository assignmentRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.shipmentRepository = shipmentRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public Route getRouteById(Long id) {
        return routeRepository.findById(id).orElse(null);
    }

    @Transactional
    public Route createRoute(Route route) {
        route.setId(null);
        route.setStatus("PLANNED");
        return routeRepository.save(route);
    }

    @Transactional
    public Route updateRoute(Long id, Route updates) {
        Route existing = routeRepository.findByIdWithLock(id).orElse(null);
        if (existing != null) {
            if (updates.getStatus() != null && !updates.getStatus().equals(existing.getStatus())) {
                throw new IllegalStateException("Прямое изменение статуса маршрута через PATCH запрещено");
            }
            if (!"PLANNED".equals(existing.getStatus())) {
                throw new IllegalStateException("Изменение параметров маршрута возможно только в статусе PLANNED");
            }
            if (updates.getDate() != null) {
                existing.setDate(updates.getDate());
            }
            if (updates.getMaxWeight() != null) {
                double currentLoad = assignmentRepository.findByStopRouteId(id).stream()
                        .mapToDouble(a -> a.getShipment().getWeight())
                        .sum();
                if (updates.getMaxWeight() < currentLoad) {
                    throw new IllegalStateException(
                            "Новый лимит веса (" + updates.getMaxWeight() + " кг) меньше текущего веса посылок на маршруте (" + currentLoad + " кг)"
                    );
                }
                existing.setMaxWeight(updates.getMaxWeight());
            }
            return routeRepository.save(existing);
        }
        return null;
    }

    @Transactional
    public void deleteRoute(Long id) {
        Route existing = routeRepository.findByIdWithLock(id).orElse(null);
        if (existing == null) {
            return;
        }
        if (!"PLANNED".equals(existing.getStatus())) {
            throw new IllegalStateException("Удаление разрешено только для маршрута в статусе PLANNED. Текущий статус: " + existing.getStatus());
        }
        List<ShipmentAssignment> assignments = assignmentRepository.findByStopRouteId(id);
        for (ShipmentAssignment assignment : assignments) {
            Shipment shipment = assignment.getShipment();
            if ("ASSIGNED".equals(shipment.getStatus())) {
                shipment.setStatus("CREATED");
                shipmentRepository.save(shipment);
            }
        }
        assignmentRepository.deleteAll(assignments);
        routeRepository.deleteById(id);
    }

    @Transactional
    public ShipmentAssignment assignShipmentToRouteStop(Long routeId, Long stopId, Long shipmentId) {
        Route route = routeRepository.findByIdWithLock(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Маршрут с ID " + routeId + " не найден"));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new IllegalArgumentException("Остановка с ID " + stopId + " не найдена"));
        Shipment shipment = shipmentRepository.findByIdWithLock(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Отправление с ID " + shipmentId + " не найдено"));

        if (!stop.getRoute().getId().equals(routeId)) {
            throw new IllegalArgumentException("Остановка с ID " + stopId + " не принадлежит маршруту с ID " + routeId);
        }

        if (!"PLANNED".equals(route.getStatus())) {
            throw new IllegalStateException("Назначение невозможно: маршрут находится в статусе " + route.getStatus() + " (ожидается PLANNED)");
        }

        if (!"CREATED".equals(shipment.getStatus())) {
            throw new IllegalStateException("Отправление не может быть назначено, так как имеет статус " + shipment.getStatus() + " (ожидается CREATED)");
        }

        if (!stop.getAddress().trim().equalsIgnoreCase(shipment.getAddress().trim())) {
            throw new IllegalStateException(
                    "Адрес отправления (" + shipment.getAddress() + ") не совпадает с адресом остановки (" + stop.getAddress() + ")"
            );
        }

        double currentLoad = assignmentRepository.findByStopRouteId(routeId).stream()
                .mapToDouble(a -> a.getShipment().getWeight())
                .sum();

        if (currentLoad + shipment.getWeight() > route.getMaxWeight()) {
            throw new IllegalStateException(String.format(
                    "Превышен лимит веса маршрута! Текущая загрузка: %.2f кг, вес посылки: %.2f кг, лимит маршрута: %.2f кг",
                    currentLoad, shipment.getWeight(), route.getMaxWeight()
            ));
        }

        shipment.setStatus("ASSIGNED");
        shipmentRepository.save(shipment);

        ShipmentAssignment assignment = new ShipmentAssignment(null, shipment, stop, "ASSIGNED");
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public Route startRoute(Long routeId) {
        Route route = routeRepository.findByIdWithLock(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Маршрут с ID " + routeId + " не найден"));

        if (!"PLANNED".equals(route.getStatus())) {
            throw new IllegalStateException("Запустить можно только маршрут в статусе PLANNED. Текущий статус: " + route.getStatus());
        }

        List<Stop> stops = stopRepository.findByRouteIdOrderBySequenceNumberAsc(routeId);
        if (stops.isEmpty()) {
            throw new IllegalStateException("Невозможно запустить маршрут без остановок");
        }

        List<ShipmentAssignment> assignments = assignmentRepository.findByStopRouteId(routeId);
        if (assignments.isEmpty()) {
            throw new IllegalStateException("Невозможно запустить пустой маршрут без назначенных отправлений");
        }

        route.setStatus("IN_PROGRESS");
        Route savedRoute = routeRepository.save(route);

        for (ShipmentAssignment assignment : assignments) {
            assignment.setStatus("IN_TRANSIT");
            assignmentRepository.save(assignment);

            Shipment shipment = assignment.getShipment();
            shipment.setStatus("IN_TRANSIT");
            shipmentRepository.save(shipment);
        }

        return savedRoute;
    }
}