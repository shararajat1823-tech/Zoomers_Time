using Xunit;

// Integration tests share a single PostgreSQL database, so they must not run in
// parallel (one test truncating the DB would corrupt another's state).
[assembly: CollectionBehavior(DisableTestParallelization = true)]
