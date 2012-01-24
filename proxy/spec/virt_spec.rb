require 'candlepin_scenarios'
require 'virt_fixture'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'Standalone Virt-Limit Subscriptions' do
  include CandlepinMethods
  include CandlepinScenarios
  include VirtFixture

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?

    # Setup two virt host consumers:
    @host1 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host1_client = Candlepin.new(username=nil, password=nil,
        cert=@host1['idCert']['cert'],
        key=@host1['idCert']['key'])

    @host2 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host2_client = Candlepin.new(username=nil, password=nil,
        cert=@host2['idCert']['cert'],
        key=@host2['idCert']['key'])

    pools = @host1_client.list_pools :consumer => @host1['uuid']
    @host_ent = @host1_client.consume_pool(@virt_limit_pool['id'])[0]
    # After binding the host should see no pools available:
    pools = @host1_client.list_pools :consumer => @host1['uuid']
    # one should remain
    pools.length.should == 1

    pools = @host2_client.list_pools :consumer => @host2['uuid']
    host2_ent = @host2_client.consume_pool(@virt_limit_pool['id'])[0]
    # After binding the host should see no pools available:
    pools = @host2_client.list_pools :consumer => @host2['uuid']

    # Link the host and the guest:
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    @cp.get_consumer_guests(@host1['uuid']).length.should == 1

    # Find the host-restricted pool:
    pools = @guest1_client.list_pools :consumer => @guest1['uuid']

    pools.should have(3).things
    @guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

  end

  it 'should create a virt_only pool for hosts guests' do
    # Get the attribute that indicates which host:
    requires_host = @guest_pool['attributes'].find_all {
      |i| i['name'] == 'requires_host' }[0]
    requires_host['value'].should == @host1['uuid']

    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'])

    # Should not be able to use the pool as this guest is not on the correct
    # host:
    lambda do
      @guest2_client.consume_pool(@guest_pool['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should list host restricted pool only for its guests' do
    # Other guest shouldn't be able to see the virt sub-pool:
    pools = @guest2_client.list_pools :consumer => @guest2['uuid']
    pools.should have(2).things
  end

  it 'should revoke guest entitlements when host unbinds' do
    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    @host1_client.unbind_entitlement(@host_ent['id'])

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should not revoke guest entitlements when host stops reporting guest ID' do
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @host1_client.update_consumer({:guestIds => []})

    # Entitlement should be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should obtain a new entitlement when guest is migrated to another host' do

    @guest1_client.update_consumer({:installedProducts => @installed_product_list})
    @guest1_client.consume_pool(@guest_pool['id'])
    original_ent = @guest1_client.list_entitlements.first.id
    @guest1_client.list_entitlements.length.should == 1

    # Add guest 2 to host 1 so we can make sure that only guest1's
    # entitlements are revoked.
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}, {'guestId' => @uuid1}]});

    @guest2_client.consume_pool(@guest_pool['id'])
    @guest2_client.list_entitlements.length.should == 1



    # Host 2 reports the new guest before Host 1 reports it removed.
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    # Entitlement should still be on guest1 since it was migrated and autosubscribed
    @guest1_client.list_entitlements.length.should == 1
    # make sure we have a different entitlement than we started with
    new_ent = @guest1_client.list_entitlements.first.id
    new_ent.should_not == original_ent


    # Entitlements should have remained the same for guest 2 and its host
    # is the same.
    @guest2_client.list_entitlements.length.should == 1
  end

end
