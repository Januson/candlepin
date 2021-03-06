/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.PersistenceException;



public class PoolTest extends DatabaseTestFixture {

    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private CandlepinPoolManager poolManager;

    private Pool pool;
    private Product prod1;
    private Product prod2;
    private Owner owner;
    private Consumer consumer;
    private Subscription subscription;

    @BeforeEach
    public void createObjects() {
        beginTransaction();

        try {
            owner = new Owner("testowner");
            ownerCurator.create(owner);

            prod1 = this.createProduct(owner);
            prod2 = this.createProduct(owner);

            prod1.setProvidedProducts(Arrays.asList(prod2));

            pool = TestUtil.createPool(owner, prod1, 1000);
            subscription = TestUtil.createSubscription(owner, prod1);
            subscription.setId(Util.generateDbUUID());

            pool.setSourceSubscription(new SourceSubscription(subscription.getId(), "master"));
            poolCurator.create(pool);
            owner = pool.getOwner();

            consumer = this.createConsumer(owner);

            productCurator.create(prod1);
            poolCurator.create(pool);

            commitTransaction();
        }
        catch (RuntimeException e) {
            rollbackTransaction();
            throw e;
        }
    }

    @Test
    public void testCreate() {
        Pool lookedUp = this.getEntityManager().find(Pool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod1.getId(), lookedUp.getProductId());
    }

    @Test
    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = createPool(
            owner, prod1, -1L, TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30)
        );

        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testIsOverflowing() {
        Pool duplicatePool = createPool(
            owner, prod1, -1L, TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30)
        );

        assertFalse(duplicatePool.isOverflowing());
    }

    @Test
    public void testQuantityAdjust() {
        Pool p = new Pool();
        p.setQuantity(10L);
        Long q = p.adjustQuantity(2L);
        assertEquals((Long) 12L, (Long) q);

        q = p.adjustQuantity(-2L);
        assertEquals((Long) 8L, (Long) q);
    }

    @Test
    public void testQuantityAdjustNonNegative() {
        Pool p = new Pool();
        p.setQuantity(0L);
        Long q = p.adjustQuantity(-2L);
        assertEquals((Long) 0L, (Long) q);
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = this.createProduct(owner);

        Pool unlimitedPool = createPool(
            owner, newProduct, -1L, TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30)
        );

        poolCurator.create(unlimitedPool);
        assertTrue(unlimitedPool.entitlementsAvailable(1));
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() throws Exception {
        Long numAvailEntitlements = 1L;
        Product newProduct = this.createProduct(owner);

        Pool consumerPool = createPool(owner, newProduct, numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));

        consumerPool = poolCurator.create(consumerPool);

        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(consumerPool.getId(), 1);
        poolManager.entitleByPools(consumer, pQs);

        consumerPool = poolCurator.get(consumerPool.getId());
        assertFalse(consumerPool.entitlementsAvailable(1));
        assertEquals(1, consumerPool.getEntitlements().size());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() throws Exception {
        Long numAvailEntitlements = 1L;

        Product newProduct = this.createProduct(owner);

        Pool consumerPool = createPool(
            owner,
            newProduct,
            numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2050, 11, 30)
        );

        poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(consumerPool.getId(), 1);
        poolManager.entitleByPools(consumer, pQs);

        assertEquals(1, consumerCurator.get(consumer.getId()).getEntitlements().size());
    }

    // test subscription product changed exception

    @Test
    public void testLookupPoolsProvidingProduct() {

        Product childProduct = this.createProduct("2", "product-2", owner);

        Product parentProduct = TestUtil.createProduct("1", "product-1");
        parentProduct.setProvidedProducts(Arrays.asList(childProduct));

        parentProduct = this.createProduct(parentProduct, owner);
        Pool pool = TestUtil.createPool(owner, parentProduct, 5);
        poolCurator.create(pool);


        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            null, owner, childProduct.getId(), null
        );
        assertEquals(1, results.size());
        assertEquals(pool.getId(), results.get(0).getId());
    }

    /**
     * After creating a new pool object, test is made to determine whether
     * the created and updated values are present and not null.
     */
    @Test
    public void testCreationTimestamp() {
        Product newProduct = this.createProduct(owner);

        Pool pool = createPool(
            owner, newProduct, 1L, TestUtil.createDate(2011, 3, 30), TestUtil.createDate(2022, 11, 29)
        );

        poolCurator.create(pool);
        assertNotNull(pool.getCreated());
    }

    @Test
    public void testInitialUpdateTimestamp() {
        Product newProduct = this.createProduct(owner);

        Pool pool = createPool(
            owner, newProduct, 1L, TestUtil.createDate(2011, 3, 30), TestUtil.createDate(2022, 11, 29)
        );

        pool = poolCurator.create(pool);
        assertNotNull(pool.getUpdated());
    }

    /**
     * After updating an existing pool object, test is made to determine whether
     * the updated value has changed
     */
    @Test
    public void testSubsequentUpdateTimestamp() {
        Product newProduct = this.createProduct(owner);

        Pool pool = createPool(
            owner, newProduct, 1L, TestUtil.createDate(2011, 3, 30), TestUtil.createDate(2022, 11, 29)
        );

        pool = poolCurator.create(pool);

        // set updated to 10 minutes ago
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        pool.setUpdated(calendar.getTime());

        Date updated = (Date) pool.getUpdated().clone();
        pool.setQuantity(23L);
        pool = poolCurator.merge(pool);

        assertFalse(updated.getTime() == pool.getUpdated().getTime());
    }

    @Test
    public void testProvidedProductImmutability() {
        Product parentProduct = TestUtil.createProduct("1", "product-1");
        Product providedProduct = this.createProduct("provided", "Child 1", owner);
        parentProduct.setProvidedProducts(Arrays.asList(providedProduct));

        Product childProduct1 = this.createProduct("child1", "child1", owner);

        parentProduct = this.createProduct(parentProduct, owner);
        Pool pool = TestUtil.createPool(owner, parentProduct, 5);
        poolCurator.create(pool);
        pool = poolCurator.get(pool.getId());
        assertEquals(1, pool.getProduct().getProvidedProducts().size());

        // provided products are immutable set.
        pool.getProduct().addProvidedProduct(childProduct1);
        Pool finalPool = pool;
        Assertions.assertThrows(PersistenceException.class, () ->poolCurator.merge(finalPool));
    }

    // sunny test - real rules not invoked here. Can only be sure the counts are recorded.
    // Rule tests already exist for quantity filter.
    // Will use spec tests to see if quantity rules are followed in this scenario.
    @Test
    public void testEntitlementQuantityChange() throws EntitlementRefusedException {
        Map<String, Integer> pQs = new HashMap<>();
        pQs.put(pool.getId(), 3);
        List<Entitlement> entitlements = poolManager.entitleByPools(consumer, pQs);

        Entitlement ent = entitlements.get(0);
        assertTrue(ent.getQuantity() == 3);
        poolManager.adjustEntitlementQuantity(consumer, ent, 5);
        Entitlement ent2 = entitlementCurator.get(ent.getId());
        assertTrue(ent2.getQuantity() == 5);
        Pool pool2 = poolCurator.get(pool.getId());
        assertTrue(pool2.getConsumed() == 5);
        assertTrue(pool2.getEntitlements().size() == 1);
    }

    @Test
    public void testPoolType() {
        pool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        assertEquals(PoolType.BONUS, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        assertEquals(PoolType.ENTITLEMENT_DERIVED, pool.getType());

        pool.setSourceEntitlement(null);
        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.STACK_DERIVED, pool.getType());

        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        pool.setSourceStack(null);
        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());

        pool.removeAttribute(Pool.Attributes.DERIVED_POOL);
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setSourceEntitlement(null);
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.NORMAL, pool.getType());
    }

    @Test
    public void testSetSubIdFromValue() {
        pool.setSubscriptionId("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionId());
        // subkey should be unchanged
        assertEquals("master", pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testSetSubIdFromNull() {
        pool.setSourceSubscription(null);
        pool.setSubscriptionId("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionId());
        // subkey should be null
        assertNull(pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testSetSubIdNullRemoval() {
        pool.getSourceSubscription().setSubscriptionSubKey(null);
        pool.setSubscriptionId(null);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubIdNullEmptyString() {
        pool.getSourceSubscription().setSubscriptionSubKey(null);
        pool.setSubscriptionId("");
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubKeyFromValue() {
        pool.setSubscriptionSubKey("testkey");
        assertEquals("testkey", pool.getSourceSubscription().getSubscriptionSubKey());
        // subkey should be unchanged
        assertEquals(subscription.getId(), pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testSetSubKeyFromNull() {
        pool.setSourceSubscription(null);
        pool.setSubscriptionSubKey("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionSubKey());
        // subkey should be null
        assertNull(pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testSetSubKeyNullRemoval() {
        pool.getSourceSubscription().setSubscriptionId(null);
        pool.setSubscriptionSubKey(null);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubKeyNullEmptyString() {
        pool.getSourceSubscription().setSubscriptionId(null);
        pool.setSubscriptionSubKey("");
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testIsDerivedPool() {
        Pool derivedPool = TestUtil.createPool(owner, prod1, 1000);
        derivedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        assertTrue(derivedPool.isDerived());
    }

    @Test
    public void testIsNotDerivedPool() {
        Pool derivedPool = TestUtil.createPool(owner, prod1, 1000);

        assertFalse(derivedPool.isDerived());
    }
}
