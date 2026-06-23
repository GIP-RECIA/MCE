package fr.recia.mce.api.escomceapi.db.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;

@Repository
public interface CerbereConfirmationRepository extends AbstractRepository<CerbereConfirmation, Long> {

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NOT NULL ORDER BY cc.confirmation DESC")
    List<CerbereConfirmation> findConfirmedByPersonId(@Param("personId") Long personId);

}
