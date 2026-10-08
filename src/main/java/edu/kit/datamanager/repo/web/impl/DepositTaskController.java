package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.service.DepositTaskService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/my-deposit-tasks")
public class DepositTaskController {
    private final DepositTaskService tasks;
    public DepositTaskController(DepositTaskService tasks){this.tasks=tasks;}
    @GetMapping public DepositTaskService.Tasks mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return tasks.mine(page,size);}
}
