package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.service.ScientificCollectionService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/public/collections")
public class PublicCollectionController {
    private final ScientificCollectionService service;
    public PublicCollectionController(ScientificCollectionService service) {this.service=service;}
    @GetMapping public ScientificCollectionService.CollectionPage list(@RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.list(true,kind,page,size);}
    @GetMapping("/{id}") public ScientificCollectionService.DatasetPage detail(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.datasets(id,true,page,size);}
}
