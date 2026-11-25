package fr.gakkel.swarmsimulator.swarmserver;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import fr.gakkel.swarmsimulator.swarmserver.simulation.SimulationLoop;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Checks the dependency rule of the server (#174): the domain is plain Java at the center,
 * the simulation engine only uses the domain, and the adapters ({@code server} for gRPC,
 * {@code config} for files and environment variables) stay outside.
 *
 * <p>The rules are grouped by theme in nested classes, each included with {@link ArchTests}.
 */
@AnalyzeClasses(
        packages = "fr.gakkel.swarmsimulator.swarmserver",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..swarmserver.domain..";
    private static final String SIMULATION = "..swarmserver.simulation..";
    private static final String SERVER = "..swarmserver.server..";
    private static final String CONFIG = "..swarmserver.config..";
    private static final String SCORING = "..swarmserver.scoring..";

    private static final String GRPC = "io.grpc..";
    private static final String PROTOBUF = "com.google.protobuf..";
    private static final String SPRING = "org.springframework..";

    /**
     * Classes generated from the protobuf contract (module {@code contracts}): one package for
     * each {@code java_package} in {@code contracts/src/main/proto/}. Add a new one here.
     */
    private static final String[] GENERATED_CONTRACT = {
        "io.gakkel.swarm.contracts..", "fr.gakkel.swarmsimulator.contracts.."
    };

    @ArchTest
    static final ArchTests domain = ArchTests.in(DomainRules.class);

    @ArchTest
    static final ArchTests engine = ArchTests.in(EngineRules.class);

    @ArchTest
    static final ArchTests adapters = ArchTests.in(AdapterRules.class);

    @ArchTest
    static final ArchRule server_packages_have_no_cycles =
            slices().matching("fr.gakkel.swarmsimulator.swarmserver.(*)..")
                    .should().beFreeOfCycles();

    /** The domain is at the center: it knows no other package of the server. */
    static final class DomainRules {

        @ArchTest
        static final ArchRule domain_depends_on_no_other_server_package =
                noClasses().that().resideInAPackage(DOMAIN)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(SIMULATION, SERVER, CONFIG, SCORING);
    }

    /** The engine (domain and simulation) runs alone: no adapter, framework or thread. */
    static final class EngineRules {

        @ArchTest
        static final ArchRule simulation_does_not_depend_on_adapters =
                noClasses().that().resideInAPackage(SIMULATION)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(SERVER, CONFIG, SCORING);

        @ArchTest
        static final ArchRule engine_does_not_use_grpc_or_frameworks =
                noClasses().that().resideInAnyPackage(DOMAIN, SIMULATION)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(GRPC, PROTOBUF, SPRING);

        /**
         * Known exception: {@link SimulationLoop} is the live clock that paces the simulation
         * for Unity, not engine logic. It will move out of the engine in #186; then remove this
         * exception.
         */
        @ArchTest
        static final ArchRule engine_does_not_use_threads_or_schedulers =
                noClasses().that().resideInAnyPackage(DOMAIN, SIMULATION)
                        .and().doNotBelongToAnyOf(SimulationLoop.class)
                        .should().dependOnClassesThat().areAssignableTo(Executor.class)
                        .orShould().dependOnClassesThat()
                        .belongToAnyOf(Executors.class, Thread.class, CompletableFuture.class);
    }

    /**
     * The adapters stay apart: only server speaks the contract, config never calls server or
     * scoring.
     */
    static final class AdapterRules {

        @ArchTest
        static final ArchRule only_server_uses_the_generated_contract =
                noClasses().that().resideOutsideOfPackage(SERVER)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(GENERATED_CONTRACT);

        @ArchTest
        static final ArchRule config_does_not_depend_on_server_or_scoring =
                noClasses().that().resideInAPackage(CONFIG)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(SERVER, SCORING);
    }
}
