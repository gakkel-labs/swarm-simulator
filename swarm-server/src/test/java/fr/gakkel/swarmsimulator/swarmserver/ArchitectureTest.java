package fr.gakkel.swarmsimulator.swarmserver;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Checks the dependency rule of the server (#174): the domain is plain Java at the center,
 * the simulation engine only uses the domain, and the adapters ({@code server} for gRPC,
 * {@code config} for files and environment variables) stay outside.
 */
@AnalyzeClasses(
        packages = "fr.gakkel.swarmsimulator.swarmserver",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..swarmserver.domain..";
    private static final String SIMULATION = "..swarmserver.simulation..";
    private static final String SERVER = "..swarmserver.server..";
    private static final String CONFIG = "..swarmserver.config..";

    /** Classes generated from the protobuf contract (module {@code contracts}). */
    private static final String[] GENERATED_CONTRACT = {
        "io.gakkel.swarm.contracts..", "fr.gakkel.swarmsimulator.contracts.."
    };

    @ArchTest
    static final ArchRule domain_depends_on_no_other_server_package =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(SIMULATION, SERVER, CONFIG);

    @ArchTest
    static final ArchRule simulation_does_not_depend_on_adapters =
            noClasses().that().resideInAPackage(SIMULATION)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(SERVER, CONFIG);

    @ArchTest
    static final ArchRule engine_does_not_use_grpc_or_frameworks =
            noClasses().that().resideInAnyPackage(DOMAIN, SIMULATION)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("io.grpc..", "com.google.protobuf..",
                            "org.springframework..");

    /**
     * Known exception: {@code SimulationLoop} is the live clock (one step every 33 ms for Unity),
     * not engine logic. It will move out of the engine in #186; then remove this exception.
     */
    @ArchTest
    static final ArchRule engine_does_not_use_schedulers =
            noClasses().that().resideInAnyPackage(DOMAIN, SIMULATION)
                    .and().doNotHaveFullyQualifiedName(
                            "fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationLoop")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.concurrent.ScheduledExecutorService")
                    .orShould().dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.concurrent.ExecutorService")
                    .orShould().dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.concurrent.Executors");

    @ArchTest
    static final ArchRule only_server_uses_the_generated_contract =
            noClasses().that().resideOutsideOfPackage(SERVER)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(GENERATED_CONTRACT);

    @ArchTest
    static final ArchRule server_packages_have_no_cycles =
            slices().matching("fr.gakkel.swarmsimulator.swarmserver.(*)..")
                    .should().beFreeOfCycles();
}
