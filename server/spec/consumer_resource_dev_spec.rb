# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Dev Resource' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @username = random_string("user")
    @consumername = random_string("dev_consumer")
    @user = user_client(@owner, @username)

    # active subscription to allow this all to work
    active_prod = create_product()
    @paid_pool = create_pool_and_subscription(@owner['key'], active_prod.id, 10)
    pools = @cp.list_owner_pools(@owner['key'])
    pools.length.should == 1

    @dev_product = create_product("dev_product",
                                  "Dev Product",
                                  {:attributes => { :expires_after => "60"}})
    @p_product1 = create_product("p_product_1",
                                  "Provided Product 1")
    @p_product2 = create_product("p_product",
                                  "Provided Product 2")
    @consumer = consumer_client(@user, @consumername, :system, 'dev_user', facts= {:dev_sku => "dev_product"})
    installed = [
        {'productId' => @p_product1.id, 'productName' => @p_product1.name},
        {'productId' => @p_product2.id, 'productName' => @p_product2.name}]
    @consumer.update_consumer({:installedProducts => installed})
  end

  it 'should create entitlement to newly created dev pool' do
    pending("candlepin running in standalone mode") if not is_hosted?

    @consumer.consume_product()
    entitlements = @consumer.list_entitlements()
    entitlements.length.should == 1
    new_pool = entitlements[0].pool
    new_pool.type.should == "DEVELOPMENT"
    new_pool.product.id.should == "dev_product"
    new_pool.providedProducts.length.should == 2
  end

  it 'should create new entitlement on additional auto attach' do
    pending("candlepin running in standalone mode") if not is_hosted?

    @consumer.consume_product()
    entitlements = @consumer.list_entitlements()
    entitlements.length.should == 1
    first_ent = entitlements[0]
    first_pool = first_ent.pool
    first_pool.type.should == "DEVELOPMENT"
    first_pool.product.id.should == "dev_product"
    first_pool.providedProducts.length.should == 2

    @consumer.consume_product()
    entitlements = @consumer.list_entitlements()
    entitlements.length.should == 1
    new_ent = entitlements[0]
    new_pool = new_ent.pool
    new_pool.type.should == "DEVELOPMENT"
    new_pool.product.id.should == "dev_product"
    new_pool.providedProducts.length.should == 2

    new_ent.id.should_not == first_ent.id
    new_pool.id.should_not == first_pool.id
  end

  it 'should recreate entitlement with existing ent from paid sub' do
    pending("candlepin running in standalone mode") if not is_hosted?

    @consumer.consume_product()
    entitlements = @consumer.list_entitlements()
    entitlements.length.should == 1
    new_pool = entitlements[0].pool
    new_pool.type.should == "DEVELOPMENT"
    new_pool.product.id.should == "dev_product"
    new_pool.providedProducts.length.should == 2

    @consumer.consume_pool(@paid_pool.id, {:quantity => 1})
    entitlements = @consumer.list_entitlements()
    entitlements.length.should == 2

    @consumer.consume_product()
    entitlements = @consumer.list_entitlements()
    entitlements.length.should == 2

    entitlements.each do |ent|
        ent.pool.id.should_not == new_pool.id
    end
  end

end
