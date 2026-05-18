-- Dev-only seed data for manual account endpoint testing.

merge into customers target
using (
    select
        hextoraw('11111111111111111111111111111111') as id,
        'dev-fake-customer' as external_customer_reference,
        'Dev Fake Customer' as full_name,
        'dev.fake.customer@example.com' as email,
        'ACTIVE' as status,
        systimestamp as created_at,
        systimestamp as updated_at,
        0 as version
    from dual
) source
on (target.id = source.id)
when not matched then
    insert (
        id,
        external_customer_reference,
        full_name,
        email,
        status,
        created_at,
        updated_at,
        version
    )
    values (
        source.id,
        source.external_customer_reference,
        source.full_name,
        source.email,
        source.status,
        source.created_at,
        source.updated_at,
        source.version
    );
