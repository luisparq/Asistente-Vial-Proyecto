package com.avigem.diagnostics.infrastructure.persistence.adapter;

import com.avigem.diagnostics.domain.model.Device;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import com.avigem.diagnostics.domain.port.out.DeviceRepository;
import com.avigem.diagnostics.infrastructure.persistence.mapper.DeviceMapper;
import com.avigem.diagnostics.infrastructure.persistence.repository.JpaDeviceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DeviceRepositoryAdapter implements DeviceRepository {

    private final JpaDeviceRepository jpa;
    private final DeviceMapper mapper;

    public DeviceRepositoryAdapter(JpaDeviceRepository jpa, DeviceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Device save(Device device) {
        return mapper.toDomain(jpa.save(mapper.toEntity(device)));
    }

    @Override
    public Optional<Device> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Device> findByDeviceCode(String deviceCode) {
        return jpa.findByDeviceCode(deviceCode).map(mapper::toDomain);
    }

    @Override
    public boolean existsByDeviceCode(String deviceCode) {
        return jpa.existsByDeviceCode(deviceCode);
    }

    @Override
    public PageResult<Device> findAll(PageQuery query) {
        Page<Device> page = jpa.findAll(toPageable(query)).map(mapper::toDomain);
        return toPageResult(page);
    }

    private Pageable toPageable(PageQuery query) {
        Sort sort = query.ascending()
            ? Sort.by(query.sortBy()).ascending()
            : Sort.by(query.sortBy()).descending();
        return PageRequest.of(query.page(), query.size(), sort);
    }

    private <T> PageResult<T> toPageResult(Page<T> page) {
        return new PageResult<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }
}
