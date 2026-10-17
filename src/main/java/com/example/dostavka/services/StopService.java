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
public class StopService {

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final ShipmentAssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;

    public StopService(StopRepository stopRepository,
                       RouteRepository routeRepository,
                       ShipmentAssignmentRepository assignmentRepository,
                       ShipmentRepository shipmentRepository) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.assignmentRepository = assignmentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    public List<Stop> getAllStops() {
        return stopRepository.findAll();
    }

    public Stop getStopById(Long id) {
        return stopRepository.findById(id).orElse(null);
    }

    @Transactional
    public Stop createStop(Stop stop, Long routeId) {
        Route route = routeRepository.findByIdWithLock(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Маршрут с ID " + routeId + " не найден"));
        if (!"PLANNED".equals(route.getStatus())) {
            throw new IllegalStateException("Добавление остановок возможно только для маршрута в статусе PLANNED");
        }
        stop.setId(null);
        stop.setRoute(route);
        return stopRepository.save(stop);
    }

    @Transactional
    public Stop updateStop(Long id, Stop updates) {
        Stop existing = stopRepository.findById(id).orElse(null);
        if (existing != null) {
            if (!"PLANNED".equals(existing.getRoute().getStatus())) {
                throw new IllegalStateException("Изменение остановки возможно только для маршрута в статусе PLANNED");
            }
            if (updates.getAddress() != null && !updates.getAddress().trim().equalsIgnoreCase(existing.getAddress().trim())) {
                List<ShipmentAssignment> stopAssignments = assignmentRepository.findByStopId(id);
                if (!stopAssignments.isEmpty()) {
                    throw new IllegalStateException("Нельзя изменить адрес остановки, к которой уже привязаны отправления");
                }
                existing.setAddress(updates.getAddress());
            }
            if (updates.getSequenceNumber() != null) {
                existing.setSequenceNumber(updates.getSequenceNumber());
            }
            return stopRepository.save(existing);
        }
        return null;
    }

    @Transactional
    public void deleteStop(Long id) {
        Stop existing = stopRepository.findById(id).orElse(null);
        if (existing == null) {
            return;
        }
        if (!"PLANNED".equals(existing.getRoute().getStatus())) {
            throw new IllegalStateException("Удаление остановки возможно только для маршрута в статусе PLANNED");
        }
        List<ShipmentAssignment> assignments = assignmentRepository.findByStopId(id);
        for (ShipmentAssignment assignment : assignments) {
            Shipment shipment = assignment.getShipment();
            if ("ASSIGNED".equals(shipment.getStatus())) {
                shipment.setStatus("CREATED");
                shipmentRepository.save(shipment);
            }
        }
        assignmentRepository.deleteAll(assignments);
        stopRepository.deleteById(id);
    }

    @Transactional
    public List<ShipmentAssignment> completeStopDelivery(Long stopId) {
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new IllegalArgumentException("Остановка с ID " + stopId + " не найдена"));

        Route route = routeRepository.findByIdWithLock(stop.getRoute().getId())
                .orElseThrow(() -> new IllegalArgumentException("Маршрут не найден"));
        if (!"IN_PROGRESS".equals(route.getStatus())) {
            throw new IllegalStateException("Выполнение остановки возможно только для активного маршрута (IN_PROGRESS). Текущий статус маршрута: " + route.getStatus());
        }

        List<ShipmentAssignment> stopAssignments = assignmentRepository.findByStopId(stopId);
        if (stopAssignments.isEmpty()) {
            throw new IllegalStateException("На данной остановке нет назначенных отправлений для выдачи");
        }

        boolean alreadyDelivered = stopAssignments.stream()
                .allMatch(a -> "DELIVERED".equals(a.getStatus()));
        if (alreadyDelivered) {
            throw new IllegalStateException("Все отправления на этой остановке уже были доставлены ранее");
        }

        List<ShipmentAssignment> routeAssignments = assignmentRepository.findByStopRouteId(route.getId());
        for (ShipmentAssignment assignment : routeAssignments) {
            if (assignment.getStop().getSequenceNumber() < stop.getSequenceNumber()
                    && !"DELIVERED".equals(assignment.getStatus())) {
                throw new IllegalStateException(
                        "Нарушена последовательность маршрута: сначала завершите доставку на предыдущей остановке №"
                                + assignment.getStop().getSequenceNumber()
                );
            }
        }

        for (ShipmentAssignment assignment : stopAssignments) {
            assignment.setStatus("DELIVERED");
            assignmentRepository.save(assignment);

            Shipment shipment = assignment.getShipment();
            shipment.setStatus("DELIVERED");
            shipmentRepository.save(shipment);
        }

        List<ShipmentAssignment> updatedRouteAssignments = assignmentRepository.findByStopRouteId(route.getId());
        boolean allRouteDelivered = updatedRouteAssignments.stream()
                .allMatch(a -> "DELIVERED".equals(a.getStatus()));
        if (allRouteDelivered) {
            route.setStatus("COMPLETED");
            routeRepository.save(route);
        }

        return stopAssignments;
    }
}